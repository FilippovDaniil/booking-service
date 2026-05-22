# ============================================================
#  rancher/build-and-load.ps1 — Сборка и загрузка образа в Rancher Desktop
#
#  ЗАЧЕМ ЭТОТ СКРИПТ:
#  Rancher Desktop использует Docker daemon ВНУТРИ Linux VM (WSL).
#  Когда вы запускаете `docker build` на Windows, образ попадает в Docker Desktop
#  (или в Windows Docker context), но НЕ в VM Rancher Desktop.
#  K8s (k3s) внутри Rancher Desktop видит только образы из СВОЕГО Docker daemon в VM.
#
#  Поэтому нужна цепочка: Windows docker build → сохранить tar → загрузить в VM.
#
#  ЗАПУСК (из корня проекта, где лежит Dockerfile):
#    .\rancher\build-and-load.ps1
#    .\rancher\build-and-load.ps1 -Tag "2.0.0"   # другой тег
#
#  ПОСЛЕ ПЕРВОГО ЗАПУСКА: kubectl apply -f rancher/k8s/
#  ПОСЛЕ ОБНОВЛЕНИЯ КОДА:
#    .\rancher\build-and-load.ps1
#    kubectl rollout restart deployment/booking-app -n pet-booking
# ============================================================

param(
    # Тег образа. По умолчанию 1.0.0. Должен совпадать с image: в 08-app.yaml!
    [string]$Tag = "1.0.0"
)

$ImageName = "booking-service"          # Имя образа (должно совпадать с image: в 08-app.yaml)
$FullImage = "${ImageName}:${Tag}"       # Полное имя: booking-service:1.0.0
$TarFile   = "${ImageName}.tar"          # Имя tar-файла
$TarPath   = "$env:TEMP\$TarFile"        # Путь на Windows: C:\Users\...\AppData\Local\Temp\booking-service.tar
# Тот же файл, но видимый из Linux VM через WSL mount.
# /mnt/c/ = C:\ (примонтирован в Linux VM)
$LinuxPath = "/mnt/c/Users/$env:USERNAME/AppData/Local/Temp/$TarFile"

Write-Host ""
Write-Host "=== Шаг 1: Сборка образа $FullImage ===" -ForegroundColor Cyan
Write-Host "Dockerfile: ./Dockerfile, контекст: . (корень проекта)"
# --provenance=false : ОБЯЗАТЕЛЬНЫЙ флаг!
# Без него BuildKit создаёт "manifest list" (мультиплатформенный манифест).
# k3s с imagePullPolicy: Never не находит такой образ → ErrImageNeverPull.
# С --provenance=false создаётся обычный одиночный манифест → всё работает.
docker build --provenance=false -t $FullImage -f Dockerfile .
if (-not $?) {
    Write-Host "ОШИБКА: сборка образа провалилась" -ForegroundColor Red
    exit 1
}

Write-Host ""
Write-Host "=== Шаг 2: Сохранение образа в файл ===" -ForegroundColor Cyan
Write-Host "Путь: $TarPath"
# docker save — экспортирует образ в tar-архив.
# Сохраняем во временную папку Windows (она доступна из Linux VM).
docker save $FullImage -o $TarPath
if (-not $?) {
    Write-Host "ОШИБКА: docker save провалился" -ForegroundColor Red
    exit 1
}
Write-Host "Сохранено: $TarPath"

Write-Host ""
Write-Host "=== Шаг 3: Загрузка в Rancher Desktop VM ===" -ForegroundColor Cyan
Write-Host "Путь в VM: $LinuxPath"
# rdctl shell -- выполняет команду внутри Linux VM Rancher Desktop.
# docker load < tar-файл — импортирует образ в Docker daemon внутри VM.
# После этого k3s (K8s внутри VM) сможет использовать образ с imagePullPolicy: Never.
rdctl shell -- sh -c "docker load < $LinuxPath"
if (-not $?) {
    Write-Host "ОШИБКА: загрузка в VM провалилась" -ForegroundColor Red
    exit 1
}

Write-Host ""
Write-Host "=== Шаг 4: Проверка — образ в VM ===" -ForegroundColor Cyan
# Выводим список образов с нашим именем в VM.
# Должна появиться строка: booking-service   1.0.0   sha256:...
rdctl shell -- sh -c "docker images $ImageName"

Write-Host ""
Write-Host "=== Готово! Образ загружен в Rancher Desktop ===" -ForegroundColor Green
Write-Host ""
Write-Host "Следующие шаги:" -ForegroundColor Yellow
Write-Host "  Первый деплой:"
Write-Host "    kubectl apply -f rancher/k8s/" -ForegroundColor White
Write-Host ""
Write-Host "  Обновить уже запущенное приложение:"
Write-Host "    kubectl rollout restart deployment/booking-app -n pet-booking" -ForegroundColor White
Write-Host ""
Write-Host "  Статус стека:"
Write-Host "    kubectl get all -n pet-booking" -ForegroundColor White
