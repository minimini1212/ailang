from pydantic_settings import BaseSettings


class Settings(BaseSettings):
    # Gemini
    gemini_api_key: str

    # Supabase
    supabase_project_url: str
    supabase_publishable_secret_key: str

    # Redis
    redis_host: str = "ailang-redis"
    redis_port: int = 6379

    # JWT (Spring Boot와 동일한 시크릿)
    jwt_secret: str

    class Config:
        env_file = ".env"


settings = Settings()
