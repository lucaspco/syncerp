# SyncERP - Fase 1 (Setup)

Serviço Event-Driven de Sincronização Multi-CNPJ via Webhooks Bling.

## 📋 O que foi entregue na Fase 1

- ✅ Spring Boot 3.x com Java 21 (LTS)
- ✅ PostgreSQL 15 integrado via Docker Compose
- ✅ Flyway para migrations de banco de dados
- ✅ Spring Actuator para health checks e métricas
- ✅ Swagger UI / OpenAPI para documentação automática
- ✅ Estrutura de pacotes padrão (config, domain, repository, etc.)
- ✅ Configurações base (application.yml)
- ✅ Thread pool assíncrono configurado (AsyncConfig)
- ✅ WebClient para HTTP não-bloqueante
- ✅ Retry automático com backoff exponencial
- ✅ Tratamento global de exceções
- ✅ Docker Compose pronto para dev
- ✅ Schema inicial do banco de dados

## 🚀 Como usar

### 1. Pré-requisitos
- Java 21+
- Maven 3.9+
- Docker & Docker Compose

### 2. Compilar
```bash
mvn clean package
```

### 3. Iniciar (Docker Compose)
```bash
docker-compose up --build
```

### 4. Acessar
- **API**: http://localhost:8080/api
- **Swagger UI**: http://localhost:8080/api/swagger-ui.html
- **Health Check**: http://localhost:8080/api/actuator/health
- **Métricas**: http://localhost:8080/api/actuator/metrics
- **PostgreSQL**: localhost:5432 (user: syncerp_user, pass: syncerp_pass)

## 📁 Estrutura de Pacotes

```
com.syncerp
├── config/             # Beans, WebClient, AsyncConfig, Retry, JPA
├── domain/             # Entidades JPA (BaseEntity)
├── repository/         # Spring Data JPA Repositories
├── webhook/            # Controllers receptores de webhooks
├── event/              # ApplicationEvents internos
├── sync/               # Orquestradores de sincronização
├── integration/        # Clientes HTTP Bling
├── dedup/              # Loop prevention e deduplicação
├── api/                # REST endpoints internos
├── exception/          # Exceptions customizadas
└── util/               # Utilities e helpers
```

## 🗄️ Banco de Dados

Schema inicial inclui:
- `bling_account` - Cadastro dos 4 CNPJs com OAuth2 tokens
- `webhook_event` - Eventos recebidos do Bling
- `sync_log` - Histórico de replicações
- `dedup_window` - Janela de deduplicação (60s)
- `dead_letter` - Eventos com falha permanente

## 📊 Configurações Importantes

Arquivo: `src/main/resources/application.yml`

```yaml
syncerp:
  bling:
    api-base-url: https://api.bling.com.br/Api/v3
    timeout-ms: 10000
    retry-max-attempts: 3
    retry-initial-delay-ms: 1000
    retry-max-delay-ms: 10000
  webhook:
    response-timeout-ms: 1000
  dedup:
    window-seconds: 60
  pool:
    core-size: 5
    max-size: 10
    queue-capacity: 100
```

## 🔄 O que vem na Próxima Fase (Fase 2)

- OAuth2 Bling com refresh automático de tokens
- Cadastro e validação dos 4 CNPJs
- Endpoint de health dos CNPJs

## 📝 Logs

Logs estruturados em JSON (SLF4J + Logback):
- Console: padrão ISO8601
- Arquivo: `logs/syncerp.log` (rotativo)

## 🛠️ Troubleshooting

**PostgreSQL não conecta?**
```bash
docker-compose logs postgres
docker-compose ps
```

**Porta 8080 já em uso?**
```bash
docker-compose.yml: change ports: "8080:8080" to "9090:8080"
```

**Limpar tudo e recomeçar?**
```bash
docker-compose down -v
docker-compose up --build
```

## 📄 Licença

Confidencial - Abril 2026
