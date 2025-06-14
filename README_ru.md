[English](README.md) | [Русский](README_ru.md)

# Storog: Android-приложение для визуального мониторинга с ИИ

Storog — это Android-приложение, которое превращает ваше устройство в интеллектуальную систему визуального мониторинга. Оно использует камеру устройства для обнаружения движения в обозначенной области. При обнаружении движения Storog задействует модель ИИ Gemini для анализа сцены и может отправлять уведомления, включая изображение и анализ ИИ, в указанный Telegram-чат.

## Ключевые функции

*   **Обнаружение визуальных изменений:** Отслеживает изображение с камеры на предмет изменений по сравнению с базовым изображением.
*   **Настраиваемая чувствительность:** Позволяет пользователям устанавливать порог того, насколько сильное изменение необходимо для срабатывания оповещения.
*   **Анализ изображений с помощью ИИ:** Использует модель Gemini от Google для анализа захваченных изображений на основе определенного пользователем запроса (например, «Есть ли на изображении человек?», «Открыта ли дверь?»).
*   **Уведомления в Telegram:** Отправляет оповещения в указанный Telegram-чат, включая захваченное изображение и сгенерированное ИИ описание или анализ.
*   **Настраиваемые запросы к ИИ:** Пользователи могут адаптировать текстовый запрос, отправляемый ИИ, чтобы сфокусировать анализ на конкретных аспектах изображения.
*   **Интерфейс настроек:** Предоставляет экран настроек в приложении для конфигурации порога чувствительности, запроса к ИИ и ID чата Telegram.
*   **Предпросмотр с камеры:** Отображает живое изображение с камеры в приложении.
*   **Управление Старт/Стоп:** Простые элементы управления для активации или деактивации процесса мониторинга.

## Установка и конфигурация

Storog — это Android-приложение, созданное с использованием Gradle.

### Предварительные требования

*   Android Studio (рекомендуется последняя стабильная версия).
*   Android-устройство или эмулятор с возможностями камеры.

### Конфигурация API-ключей

Для использования функций анализа ИИ и уведомлений в Telegram необходимо настроить следующее:

1.  **API-ключ Google Gemini:**
    *   Получите API-ключ в [Google AI Studio](https://aistudio.google.com/app/apikey).
2.  **Токен Telegram-бота:**
    *   Создайте нового Telegram-бота, обратившись к [BotFather](https://t.me/botfather) в Telegram.
    *   BotFather предоставит вам API-токен.
3.  **ID чата Telegram:**
    *   Это ID чата, группы или канала, куда бот должен отправлять уведомления.
    *   Вы можете получить свой личный ID чата, запустив [userinfobot](https://t.me/userinfobot) в Telegram. Для групп или каналов могут потребоваться другие методы (например, временное добавление бота, который показывает ID чатов).

**Файл конфигурации:**

*   После получения API-ключа Gemini и токена Telegram-бота создайте файл с именем `my_config.properties` в каталоге `app/src/main/assets/` проекта.
*   Добавьте ваши ключи в этот файл в следующем формате:

    ```properties
    GEMINI_API_KEY=ВАШ_API_КЛЮЧ_GEMINI_ЗДЕСЬ
    MY_BOT_TOKEN=ВАШ_ТОКЕН_TELEGRAM_БОТА_ЗДЕСЬ
    ```

    **Примечание:** Замените `ВАШ_API_КЛЮЧ_GEMINI_ЗДЕСЬ` и `ВАШ_ТОКЕН_TELEGRAM_БОТА_ЗДЕСЬ` вашими фактическими ключами.

*   **Важно:** Добавьте `my_config.properties` в ваш файл `.gitignore`, чтобы случайно не закоммитить ваши API-ключи в систему контроля версий.
    ```
    # API keys
    app/src/main/assets/my_config.properties
    ```

**Установка целевого ID чата:**

*   ID чата Telegram настраивается внутри самого приложения.
*   Перейдите на экран «Настройки» в приложении, чтобы ввести и сохранить ваш целевой ID чата.

## Основное использование

1.  **Установите приложение:** Соберите и запустите приложение на вашем Android-устройстве или эмуляторе.
2.  **Предоставьте разрешения:** При первом запуске приложение запросит разрешение на использование камеры. Вы должны предоставить это разрешение для работы приложения.
3.  **Настройте параметры:**
    *   Перейдите на экран «Настройки».
    *   Введите ваш **ID чата Telegram**, куда вы хотите получать уведомления.
    *   По желанию, вы можете настроить **Порог изменения** (0-100%). Меньшее значение означает большую чувствительность к изменениям. По умолчанию 5%.
    *   По желанию, вы можете настроить **Запрос к ИИ** для управления анализом изображений. По умолчанию «есть ли на изображении кошка?».
    *   Сохраните ваши настройки.
4.  **Начните мониторинг:**
    *   Вернитесь на главный экран.
    *   Нажмите кнопку «Старт».
    *   Приложение захватит начальное эталонное изображение. Мониторинг теперь активен.
5.  **Мониторинг в действии:**
    *   Приложение будет постоянно сравнивать текущее изображение с камеры с эталонным изображением.
    *   Отображается текущий процент различия.
    *   Если различие превысит установленный вами порог, приложение:
        *   Отправит текущее изображение и ваш запрос к ИИ в Gemini AI для анализа.
        *   Отправит сообщение в настроенный вами Telegram-чат, включая изображение и ответ ИИ (если только ответ ИИ не предписывает иное, например, если ответ начинается с «Нет», или если достигнут лимит сообщений).
6.  **Остановите мониторинг:** Нажмите кнопку «Стоп», чтобы деактивировать мониторинг.
7.  **Справка:** Нажмите кнопку «Справка» для краткого руководства в приложении.

## Возможные будущие улучшения / Ограничения

*   **Более безопасное управление API-ключами:** Вместо файла properties в `assets`, рассмотрите возможность использования системы Android Keystore или передачи ключей через параметры сборки Gradle из файла `local.properties` (который также должен быть в `.gitignore`).
*   **Работа в фоновом режиме:** В настоящее время мониторинг активен только когда приложение находится на переднем плане. Фоновый сервис мог бы обеспечить непрерывный мониторинг.
*   **Расширенное планирование:** Позволить пользователям планировать мониторинг на определенное время или дни.
*   **Настройка уведомлений:** Больше опций для звуков уведомлений, вибрации и т.д.
*   **Поддержка нескольких камер/сцен:** Возможность настраивать и переключаться между различными отслеживаемыми областями или ракурсами камеры, если устройство имеет несколько камер.
*   **Обработка ошибок и отказоустойчивость:** Более надежная обработка ошибок сети, сбоев API и т.д.
*   **Интернационализация:** Перевод элементов интерфейса на большее количество языков (в настоящее время в исходном коде/описаниях интерфейса присутствует смесь английского и русского).
*   **Лимит сообщений:** В приложении в настоящее время жестко задан лимит в 3 сообщения Telegram перед остановкой мониторинга для предотвращения чрезмерного количества уведомлений. Это можно было бы сделать настраиваемым или использовать более сложный подход к ограничению частоты.
*   **Разбор ответа ИИ:** Логика пропуска сообщений Telegram (например, если ИИ говорит «Нет») или остановки мониторинга основана на простых префиксах строк. Это можно было бы сделать более гибким.

## Сборка проекта

1.  **Клонируйте репозиторий:**
    ```bash
    git clone https://github.com/premananda108/storog.git
    cd <каталог-репозитория>
    ```
2.  **Настройте API-ключи:** Убедитесь, что вы настроили файл `my_config.properties` в `app/src/main/assets/`, как описано в разделе «Установка и конфигурация».
3.  **Откройте в Android Studio:** Откройте корневой каталог проекта в Android Studio.
4.  **Синхронизируйте Gradle:** Позвольте Android Studio синхронизировать файлы Gradle и загрузить зависимости.
5.  **Соберите проект:**
    *   Вы можете собрать проект, используя меню «Build» в Android Studio (например, «Build» > «Make Project» или «Build» > «Build Bundle(s) / APK(s)»).
    *   В качестве альтернативы, вы можете собрать проект из командной строки, используя Gradle Wrapper:
        *   Для отладочного APK: `./gradlew assembleDebug`
        *   Для релизного APK (требуется конфигурация подписи релиза): `./gradlew assembleRelease`
6.  **Запустите:** Разверните приложение на Android-устройстве или эмуляторе.

---

## Лицензия

Этот проект лицензирован под лицензией MIT - см. файл [LICENSE](LICENSE) для подробностей.
