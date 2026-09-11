FROM python:3.11-slim

WORKDIR /app

COPY requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt

COPY . .

# 🔴 --reload 를 이미지에 넣지 않는다.
#    ① 개발용 옵션이라 파일 감시 프로세스가 상시로 돌고, 코드 변경 시 워커를 죽였다 살린다.
#    ② 그런데 이 이미지는 소스를 «COPY» 로 굽는다 — 마운트가 없으니 «다시 읽을 새 파일이
#       애초에 없다.» 즉 지금까지 감시 비용만 내고 얻는 것은 0이었다.
#    개발 중 자동 재시작이 필요하면 docker-compose.yml 에서 command 로 덮는다
#    (소스 마운트도 같이 해야 실제로 동작한다).
CMD ["uvicorn", "app.main:app", "--host", "0.0.0.0", "--port", "8000"]
