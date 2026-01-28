# server.py (исправленная версия с правильным путем WebSocket)
from flask import Flask, request, jsonify
from flask_sqlalchemy import SQLAlchemy
from flask_cors import CORS
from flask_socketio import SocketIO, emit, join_room, leave_room
from werkzeug.security import generate_password_hash, check_password_hash
import jwt
import datetime
import os
import sqlite3
from functools import wraps
import random
import string
from datetime import timezone

app = Flask(__name__)
CORS(app,
     origins="*",
     supports_credentials=True,
     allow_headers=["Content-Type", "Authorization"],
     methods=["GET", "POST", "PUT", "DELETE", "OPTIONS"])

# Настройка SocketIO - ВАЖНО: добавлен path параметр
socketio = SocketIO(app,
                    cors_allowed_origins="*",
                    async_mode='threading',
                    logger=True,
                    engineio_logger=True,  # Включаем для отладки
                    ping_timeout=60,
                    ping_interval=25,
                    path='/socket.io/')  # Явно указываем путь для WebSocket

# Настройка базы данных
basedir = os.path.abspath(os.path.dirname(__file__))
db_path = os.path.join(basedir, 'artchat.db')
app.config['SQLALCHEMY_DATABASE_URI'] = f'sqlite:///{db_path}'
app.config['SQLALCHEMY_TRACK_MODIFICATIONS'] = False
app.config['SECRET_KEY'] = 'artchat-secret-key-2024'
app.config['JWT_SECRET_KEY'] = 'jwt-artchat-secret-2024'
app.config['JWT_ACCESS_TOKEN_EXPIRES'] = datetime.timedelta(days=30)

db = SQLAlchemy(app)

# Словари для активных подключений
active_sessions = {}
active_connections = {}  # sid -> user_id


# Модели базы данных
class User(db.Model):
    __tablename__ = 'user'

    id = db.Column(db.Integer, primary_key=True)
    email = db.Column(db.String(120), unique=True, nullable=True)
    username = db.Column(db.String(80), unique=True, nullable=False)
    display_name = db.Column(db.String(80), nullable=False)
    password_hash = db.Column(db.String(200), nullable=True)
    created_at = db.Column(db.DateTime, default=lambda: datetime.datetime.now(timezone.utc))
    last_login = db.Column(db.DateTime, nullable=True)
    is_guest = db.Column(db.Boolean, default=False)
    is_online = db.Column(db.Boolean, default=False)
    last_seen = db.Column(db.DateTime, default=lambda: datetime.datetime.now(timezone.utc))
    avatar_color = db.Column(db.String(10), default='#6200EE')
    bio = db.Column(db.String(200), default='')
    avatar_url = db.Column(db.String(500), nullable=True)

    def to_dict(self):
        return {
            'id': self.id,
            'email': self.email,
            'username': self.username,
            'display_name': self.display_name,
            'is_guest': self.is_guest,
            'avatar_color': self.avatar_color,
            'bio': self.bio,
            'avatar_url': self.avatar_url,
            'is_online': self.is_online,
            'last_seen': self.last_seen.isoformat() if self.last_seen else None,
            'created_at': self.created_at.isoformat() if self.created_at else None
        }


class ChatMessage(db.Model):
    __tablename__ = 'chat_message'

    id = db.Column(db.Integer, primary_key=True)
    room = db.Column(db.String(50), default='global')
    sender_id = db.Column(db.Integer, db.ForeignKey('user.id'), nullable=False)
    sender_name = db.Column(db.String(80), nullable=False)
    message_type = db.Column(db.String(20), default='text')
    content = db.Column(db.Text, nullable=False)
    drawing_url = db.Column(db.String(500), nullable=True)
    image_url = db.Column(db.String(500), nullable=True)
    timestamp = db.Column(db.DateTime, default=lambda: datetime.datetime.now(timezone.utc), index=True)
    is_read = db.Column(db.Boolean, default=False)

    sender = db.relationship('User', backref=db.backref('messages', lazy=True))

    def to_dict(self):
        return {
            'id': self.id,
            'room': self.room,
            'sender_id': self.sender_id,
            'sender_name': self.sender_name,
            'message_type': self.message_type,
            'content': self.content,
            'drawing_url': self.drawing_url,
            'image_url': self.image_url,
            'timestamp': self.timestamp.isoformat(),
            'is_read': self.is_read
        }


class Friend(db.Model):
    __tablename__ = 'friend'

    id = db.Column(db.Integer, primary_key=True)
    user_id = db.Column(db.Integer, db.ForeignKey('user.id'), nullable=False)
    friend_id = db.Column(db.Integer, db.ForeignKey('user.id'), nullable=False)
    status = db.Column(db.String(20), default='pending')
    created_at = db.Column(db.DateTime, default=lambda: datetime.datetime.now(timezone.utc))

    user = db.relationship('User', foreign_keys=[user_id], backref=db.backref('friends_sent', lazy=True))
    friend = db.relationship('User', foreign_keys=[friend_id], backref=db.backref('friends_received', lazy=True))


# Функция для удаления и пересоздания базы данных
def recreate_database():
    """Удаляет старую базу данных и создает новую с правильной структурой"""
    print("🔄 Пересоздание базы данных...")

    if os.path.exists(db_path):
        os.remove(db_path)
        print("🗑️ Старая база данных удалена")

    with app.app_context():
        db.create_all()
        print("✅ Новая база данных создана")

        # Создаем тестового пользователя
        admin = User(
            email='test@example.com',
            username='testuser',
            display_name='Тестовый пользователь',
            is_guest=False,
            avatar_color='#6200EE',
            bio='Тестовый аккаунт',
            is_online=False,
            avatar_url=None
        )
        admin.password_hash = generate_password_hash('test123')
        db.session.add(admin)

        # Создаем тестового гостя
        guest = User(
            username='Гость_10001',
            display_name='Гость_10001',
            is_guest=True,
            avatar_color='#03DAC5',
            bio='Гостевой аккаунт',
            is_online=False,
            avatar_url=None
        )
        db.session.add(guest)

        db.session.commit()
        print("✅ Созданы тестовые пользователи")
        print("   📧 test@example.com / test123")
        print("   👤 Гость_10001")


# Декоратор для проверки токена
def token_required(f):
    @wraps(f)
    def decorated(*args, **kwargs):
        token = None

        # Проверяем токен в заголовках
        if 'Authorization' in request.headers:
            auth_header = request.headers['Authorization']
            if auth_header.startswith('Bearer '):
                token = auth_header[7:]  # Убираем 'Bearer '

        # Также проверяем в параметрах запроса
        if not token and 'token' in request.args:
            token = request.args.get('token')

        if not token:
            return jsonify({'success': False, 'message': 'Токен отсутствует'}), 401

        try:
            data = jwt.decode(token, app.config['JWT_SECRET_KEY'], algorithms=["HS256"])
            current_user = User.query.get(data['user_id'])

            if not current_user:
                return jsonify({'success': False, 'message': 'Пользователь не найден'}), 401

        except jwt.ExpiredSignatureError:
            return jsonify({'success': False, 'message': 'Срок действия токена истек'}), 401
        except jwt.InvalidTokenError:
            return jsonify({'success': False, 'message': 'Неверный токен'}), 401
        except Exception as e:
            return jsonify({'success': False, 'message': f'Ошибка проверки токена: {str(e)}'}), 401

        return f(current_user, token, *args, **kwargs)

    return decorated


# Генерация JWT токена
def generate_token(user_id):
    token = jwt.encode({
        'user_id': user_id,
        'exp': datetime.datetime.now(timezone.utc) + app.config['JWT_ACCESS_TOKEN_EXPIRES']
    }, app.config['JWT_SECRET_KEY'], algorithm="HS256")

    return token


# Утилита для создания успешного ответа
def success_response(data=None, message="Успешно"):
    response = {'success': True, 'message': message}
    if data is not None:
        response.update(data)
    return jsonify(response)


# Утилита для создания ошибки
def error_response(message, code=400):
    return jsonify({'success': False, 'message': message}), code


# ==================== API Routes ====================

@app.route('/api/health', methods=['GET'])
def health_check():
    try:
        return jsonify({
            'success': True,
            'status': 'healthy',
            'timestamp': datetime.datetime.now(timezone.utc).isoformat(),
            'version': '1.0.0',
            'message': 'Сервер работает нормально'
        })
    except Exception as e:
        return jsonify({'success': False, 'error': str(e)}), 500


@app.route('/api/register', methods=['POST'])
def register():
    try:
        data = request.get_json()

        if not data:
            return error_response('Неверный формат данных', 400)

        # Проверка обязательных полей
        required_fields = ['email', 'password', 'username', 'display_name']
        for field in required_fields:
            if not data.get(field):
                return error_response(f'Поле {field} обязательно', 400)

        # Проверка email
        if User.query.filter_by(email=data['email']).first():
            return error_response('Email уже используется', 400)

        # Проверка username
        if User.query.filter_by(username=data['username']).first():
            return error_response('Имя пользователя уже используется', 400)

        # Создание пользователя
        user = User(
            email=data['email'],
            username=data['username'],
            display_name=data['display_name'],
            is_guest=False,
            avatar_color=data.get('avatar_color', '#6200EE'),
            bio=data.get('bio', ''),
            is_online=True,
            last_seen=datetime.datetime.now(timezone.utc),
            avatar_url=data.get('avatar_url')
        )

        user.password_hash = generate_password_hash(data['password'])

        db.session.add(user)
        db.session.commit()

        # Генерация токена
        token = generate_token(user.id)

        return success_response({
            'token': token,
            'user': user.to_dict()
        }, 'Регистрация успешна')

    except Exception as e:
        db.session.rollback()
        return error_response(f'Ошибка сервера: {str(e)}', 500)


@app.route('/api/login', methods=['POST'])
def login():
    try:
        data = request.get_json()

        if not data:
            return error_response('Неверный формат данных', 400)

        email = data.get('email')
        password = data.get('password')

        if not email or not password:
            return error_response('Требуется email и пароль', 400)

        # Поиск пользователя по email
        user = User.query.filter_by(email=email).first()

        if not user:
            return error_response('Пользователь не найден', 404)

        # Проверка пароля
        if not user.password_hash or not check_password_hash(user.password_hash, password):
            return error_response('Неверный пароль', 401)

        # Обновление статуса
        user.last_login = datetime.datetime.now(timezone.utc)
        user.is_online = True
        user.last_seen = datetime.datetime.now(timezone.utc)
        db.session.commit()

        # Генерация токена
        token = generate_token(user.id)

        return success_response({
            'token': token,
            'user': user.to_dict()
        }, 'Вход выполнен успешно')

    except Exception as e:
        return error_response(f'Ошибка сервера: {str(e)}', 500)


@app.route('/api/guest', methods=['POST'])
def create_guest():
    try:
        # Генерация уникального имени гостя
        while True:
            guest_number = random.randint(10000, 99999)
            guest_username = f"Гость_{guest_number}"

            if not User.query.filter_by(username=guest_username).first():
                break

        # Создание гостевого пользователя
        guest_user = User(
            username=guest_username,
            display_name=guest_username,
            is_guest=True,
            avatar_color=f'#{random.randint(0, 0xFFFFFF):06x}',
            is_online=True,
            last_seen=datetime.datetime.now(timezone.utc),
            avatar_url=None
        )

        db.session.add(guest_user)
        db.session.commit()

        # Генерация токена
        token = generate_token(guest_user.id)

        return success_response({
            'token': token,
            'user': guest_user.to_dict()
        }, 'Гостевой аккаунт создан')

    except Exception as e:
        db.session.rollback()
        return error_response(f'Ошибка сервера: {str(e)}', 500)


@app.route('/api/profile', methods=['GET'])
@token_required
def get_profile(current_user, token):
    try:
        return success_response({
            'user': current_user.to_dict()
        })

    except Exception as e:
        return error_response(f'Ошибка сервера: {str(e)}', 500)


@app.route('/api/profile', methods=['PUT'])
@token_required
def update_profile(current_user, token):
    try:
        data = request.get_json()

        if not data:
            return error_response('Неверный формат данных', 400)

        # Обновляем поля пользователя
        if 'username' in data and data['username']:
            # Проверяем, что username уникальный
            existing_user = User.query.filter_by(username=data['username']).first()
            if existing_user and existing_user.id != current_user.id:
                return error_response('Имя пользователя уже используется', 400)
            current_user.username = data['username']

        if 'display_name' in data and data['display_name']:
            current_user.display_name = data['display_name']

        if 'avatar_color' in data and data['avatar_color']:
            current_user.avatar_color = data['avatar_color']

        if 'bio' in data:
            current_user.bio = data['bio']

        db.session.commit()

        return success_response({
            'user': current_user.to_dict()
        }, 'Профиль обновлен')

    except Exception as e:
        db.session.rollback()
        return error_response(f'Ошибка сервера: {str(e)}', 500)


@app.route('/api/change-password', methods=['POST'])
@token_required
def change_password(current_user, token):
    try:
        data = request.get_json()

        if not data:
            return error_response('Неверный формат данных', 400)

        current_password = data.get('current_password')
        new_password = data.get('new_password')
        confirm_password = data.get('confirm_password')

        if not all([current_password, new_password, confirm_password]):
            return error_response('Все поля обязательны', 400)

        # Проверка текущего пароля
        if not current_user.password_hash or not check_password_hash(current_user.password_hash, current_password):
            return error_response('Неверный текущий пароль', 401)

        # Проверка совпадения новых паролей
        if new_password != confirm_password:
            return error_response('Пароли не совпадают', 400)

        # Проверка длины пароля
        if len(new_password) < 6:
            return error_response('Пароль должен быть не менее 6 символов', 400)

        # Обновление пароля
        current_user.password_hash = generate_password_hash(new_password)
        db.session.commit()

        return success_response(message='Пароль успешно изменен')

    except Exception as e:
        db.session.rollback()
        return error_response(f'Ошибка сервера: {str(e)}', 500)


@app.route('/api/logout', methods=['POST'])
@token_required
def logout(current_user, token):
    try:
        # Обновляем статус пользователя
        current_user.is_online = False
        current_user.last_seen = datetime.datetime.now(timezone.utc)
        db.session.commit()

        return success_response(message='Выход выполнен успешно')

    except Exception as e:
        return error_response(f'Ошибка сервера: {str(e)}', 500)


@app.route('/api/chat/global/messages', methods=['GET'])
@token_required
def get_global_messages(current_user, token):
    try:
        limit = request.args.get('limit', 100, type=int)

        messages = ChatMessage.query.filter_by(room='global') \
            .order_by(ChatMessage.timestamp.desc()) \
            .limit(limit) \
            .all()

        return success_response({
            'messages': [msg.to_dict() for msg in reversed(messages)]
        })

    except Exception as e:
        return error_response(f'Ошибка сервера: {str(e)}', 500)


@app.route('/api/chat/send', methods=['POST'])
@token_required
def send_message(current_user, token):
    try:
        data = request.get_json()

        if not data:
            return error_response('Неверный формат данных', 400)

        content = data.get('content', '').strip()
        if not content:
            return error_response('Сообщение не может быть пустым', 400)

        # Создание сообщения
        message = ChatMessage(
            room=data.get('room', 'global'),
            sender_id=current_user.id,
            sender_name=current_user.display_name,
            message_type=data.get('message_type', 'text'),
            content=content,
            drawing_url=data.get('drawing_url'),
            image_url=data.get('image_url'),
            timestamp=datetime.datetime.now(timezone.utc)
        )

        db.session.add(message)
        db.session.commit()

        # Отправка через WebSocket
        message_data = message.to_dict()
        socketio.emit('new_message', message_data, room=message.room)

        return success_response({
            'message': message_data
        }, 'Сообщение отправлено')

    except Exception as e:
        db.session.rollback()
        return error_response(f'Ошибка сервера: {str(e)}', 500)


@app.route('/api/users/online', methods=['GET'])
@token_required
def get_online_users(current_user, token):
    try:
        # Получаем всех онлайн пользователей кроме текущего
        users = User.query.filter_by(is_online=True).filter(User.id != current_user.id).all()

        return success_response({
            'users': [user.to_dict() for user in users]
        })

    except Exception as e:
        return error_response(f'Ошибка сервера: {str(e)}', 500)


@app.route('/api/friends', methods=['GET'])
@token_required
def get_friends(current_user, token):
    try:
        # Получаем принятые дружеские связи
        friendships = Friend.query.filter(
            ((Friend.user_id == current_user.id) | (Friend.friend_id == current_user.id)) &
            (Friend.status == 'accepted')
        ).all()

        friends = []
        for fs in friendships:
            friend_id = fs.friend_id if fs.user_id == current_user.id else fs.user_id
            friend = User.query.get(friend_id)

            if friend:
                friends.append(friend.to_dict())

        return success_response({
            'friends': friends
        })

    except Exception as e:
        return error_response(f'Ошибка сервера: {str(e)}', 500)


# ==================== WebSocket Events ====================

@socketio.on('connect')
def handle_connect():
    """Обработчик подключения WebSocket"""
    print(f'📡 Новое WebSocket подключение: {request.sid}')

    # Отправляем событие подтверждения подключения
    emit('connected', {
        'success': True,
        'sid': request.sid,
        'message': 'WebSocket подключен успешно',
        'timestamp': datetime.datetime.now(timezone.utc).isoformat()
    })

    print(f'✅ WebSocket {request.sid}: Отправлено подтверждение подключения')


@socketio.on('disconnect')
def handle_disconnect():
    """Обработчик отключения WebSocket"""
    print(f'📡 WebSocket отключение: {request.sid}')

    # Удаляем из активных подключений
    if request.sid in active_connections:
        user_id = active_connections[request.sid]
        user_id_str = str(user_id)

        # Обновляем статус пользователя
        user = User.query.get(user_id)
        if user:
            user.is_online = False
            user.last_seen = datetime.datetime.now(timezone.utc)
            db.session.commit()

            # Удаляем из активных сессий
            if user_id_str in active_sessions:
                room = active_sessions[user_id_str].get('room', 'global')
                emit('user_left', {
                    'user_id': user.id,
                    'username': user.display_name,
                    'room': room,
                    'timestamp': datetime.datetime.now(timezone.utc).isoformat()
                }, room=room, broadcast=True)
                del active_sessions[user_id_str]

        # Удаляем из активных подключений
        del active_connections[request.sid]


@socketio.on('join')
def handle_join(data):
    """Присоединение пользователя к комнате чата"""
    try:
        user_id = data.get('user_id')
        room = data.get('room', 'global')

        print(f'👤 Пользователь {user_id} присоединяется к комнате {room}')

        if not user_id:
            emit('error', {'message': 'Не указан ID пользователя'})
            return

        # Получаем пользователя
        user = User.query.get(user_id)
        if not user:
            emit('error', {'message': 'Пользователь не найден'})
            return

        # Обновляем статус пользователя
        user.is_online = True
        user.last_seen = datetime.datetime.now(timezone.utc)
        db.session.commit()

        # Сохраняем информацию о сессии
        active_sessions[str(user_id)] = {
            'sid': request.sid,
            'room': room,
            'joined_at': datetime.datetime.now(timezone.utc)
        }

        # Сохраняем связь sid -> user_id
        active_connections[request.sid] = user_id

        # Присоединяемся к комнате
        join_room(room)
        print(f'✅ Пользователь {user.display_name} присоединился к комнате {room}')

        # Уведомляем других пользователей
        emit('user_joined', {
            'user_id': user.id,
            'username': user.display_name,
            'room': room,
            'timestamp': datetime.datetime.now(timezone.utc).isoformat()
        }, room=room, broadcast=True)

        # Отправляем подтверждение пользователю
        emit('joined', {
            'room': room,
            'message': f'Вы присоединились к комнате {room}',
            'user': user.to_dict()
        })

    except Exception as e:
        print(f'❌ Ошибка в handle_join: {str(e)}')
        emit('error', {'message': f'Ошибка присоединения: {str(e)}'})


@socketio.on('send_message')
def handle_send_message(data):
    """Обработка отправки сообщения"""
    try:
        user_id = data.get('user_id')
        room = data.get('room', 'global')
        content = data.get('content', '').strip()
        message_type = data.get('message_type', 'text')

        print(f'💬 Сообщение от {user_id}: {content[:50]}...')

        if not content:
            emit('error', {'message': 'Сообщение не может быть пустым'})
            return

        if not user_id:
            emit('error', {'message': 'Не указан ID пользователя'})
            return

        # Получаем пользователя
        user = User.query.get(user_id)
        if not user:
            emit('error', {'message': 'Пользователь не найден'})
            return

        # Создание сообщения в БД
        message = ChatMessage(
            room=room,
            sender_id=user_id,
            sender_name=user.display_name,
            message_type=message_type,
            content=content,
            drawing_url=data.get('drawing_url'),
            image_url=data.get('image_url'),
            timestamp=datetime.datetime.now(timezone.utc)
        )

        db.session.add(message)
        db.session.commit()

        # Отправка сообщения всем в комнате
        message_data = message.to_dict()
        emit('new_message', message_data, room=room, broadcast=True)

        print(f'✅ Сообщение #{message.id} отправлено в комнату {room}')

    except Exception as e:
        print(f'❌ Ошибка в send_message: {str(e)}')
        emit('error', {'message': f'Ошибка отправки сообщения: {str(e)}'})


# Добавляем тестовый эндпоинт для проверки WebSocket
@app.route('/socket.io/', methods=['GET'])
def socket_io_test():
    """Тестовый эндпоинт для проверки пути WebSocket"""
    return jsonify({
        'success': True,
        'message': 'WebSocket endpoint is available',
        'path': '/socket.io/'
    })


# ==================== Запуск приложения ====================

if __name__ == '__main__':
    print("🚀 Запуск ArtChat Server...")

    # Пересоздаем базу данных с правильной структурой
    recreate_database()

    print("""
    🎨 ArtChat Server запущен!
    ===================================
    🌐 HTTP API:  http://localhost:5000
    🔌 WebSocket: ws://localhost:5000/socket.io/

    📋 Тестовые пользователи:
    📧 Email: test@example.com
    🔑 Пароль: test123

    👤 Гость: Гость_10001

    📋 Основные эндпоинты:
    - GET  /api/health              - Проверка работы сервера
    - GET  /socket.io/              - Проверка WebSocket пути
    - POST /api/register            - Регистрация
    - POST /api/login               - Вход
    - POST /api/guest               - Гостевой режим
    - POST /api/logout              - Выход
    - GET  /api/profile             - Профиль пользователя
    - PUT  /api/profile             - Обновить профиль
    - POST /api/change-password     - Сменить пароль
    - GET  /api/chat/global/messages - История чата
    - POST /api/chat/send           - Отправить сообщение
    - GET  /api/users/online        - Онлайн пользователи
    - GET  /api/friends             - Друзья

    🔌 WebSocket события:
    - connect      - Подключение
    - disconnect   - Отключение
    - join         - Присоединение к комнате
    - send_message - Отправка сообщения

    🚀 Сервер готов к работе!
    """)

    # Запускаем сервер
    socketio.run(app,
                 host='0.0.0.0',
                 port=5000,
                 debug=True,
                 allow_unsafe_werkzeug=True,
                 use_reloader=False)