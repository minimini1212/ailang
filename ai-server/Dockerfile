FROM python:3.11-slim

WORKDIR /app

COPY requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt

COPY . .

# 🔴 --reload 를 뺐다. 파일이 바뀌면 프로세스를 다시 띄우는 «개발용» 옵션인데 이미지에
#    들어가 있었다. 운영에서는 감시 스레드가 계속 돌고, 파일이 어쩌다 바뀌면 처리 중인
#    요청을 끊으며 재시작한다.
#    개발할 때는 이미지를 고치지 말고 compose 에서 덮어쓴다:
#      command: ["uvicorn","app.main:app","--host","0.0.0.0","--port","8000","--reload"]
CMD ["uvicorn", "app.main:app", "--host", "0.0.0.0", "--port", "8000"]
