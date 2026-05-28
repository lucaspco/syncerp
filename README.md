# SyncERP

Serviço que sincroniza estoque, produtos e clientes entre 4 CNPJs no Bling via webhooks. Tudo em tempo real, sem polling, sem interface gráfica.

## O que faz

- Recebe webhooks do Bling quando algo muda em um CNPJ
- Processa de forma assíncrona (responde em < 1s)
- Replica a mudança automaticamente para os outros 3 CNPJs
- Se falhar, tenta de novo com backoff exponencial
- Se falhar mesmo assim, salva pra revisão manual

## Stack

- Java 21 + Spring Boot 3.3.0
- PostgreSQL 15
- Docker Compose
- Flyway (migrations)
- Spring Events + ThreadPool (fila assíncrona)

## Como rodar

### Com Docker (recomendado)

```bash
git clone https://github.com/lucaspco/syncerp.git
cd syncerp
cp .env.example .env
docker-compose up --build
```

Pronto. Aplicação sobe em ~15s. Swagger fica em: http://localhost:8080/api/swagger-ui.html

### Local (precisa Java 21 + PostgreSQL rodando)

```bash
cp .env.example .env
mvn clean install
mvn spring-boot:run
```

## Endpoints

### Health
```
GET /api/actuator/health
```

### Swagger UI (testa tudo lá)
```
GET /api/swagger-ui.html
```

### Histórico de eventos
```
GET /api/eventos?cnpjId=123&tipo=produto.updated&page=0&size=20
```

### Detalhe de um evento
```
GET /api/eventos/{id}
```

### Eventos que falharam
```
GET /api/eventos/falhas
```

### Reprocessar evento que falhou
```
POST /api/eventos/{id}/reprocessar
```

## Como funciona

1. **Evento chega** → POST webhook do Bling
2. **Controller valida** → Responde 200 em < 1s
3. **Publica evento interno** → Spring ApplicationEvent
4. **ThreadPool processa** → Assincronamente (5-10 threads)
5. **Worker busca dados** → Consulta API Bling (origem)
6. **Replica** → Chama API Bling dos outros 3 CNPJs
7. **Persiste resultado** → Log no PostgreSQL
8. **Se falhar** → Retry automático (até 3x com backoff)
9. **Se falhar mesmo assim** → Vai pra dead letter (GET /eventos/falhas)

## Estrutura de pastas

```
src/main/java/com/syncerp/
├── config/           → WebClient, Async, Security
├── domain/           → Entidades JPA
├── repository/       → Spring Data
├── webhook/          → POST /webhooks/bling/{cnpjId}
├── event/            → ApplicationEvent + listeners
├── sync/             → Workers (produto, estoque, cliente)
├── integration/      → HTTP clients pra API Bling
├── dedup/            → Loop prevention + dedup
├── api/              → GET /eventos endpoints
├── exception/        → Exception handlers
└── util/             → Utils + logging
```

## Prevenção de loops

Quando SyncERP replica pra CNPJ-B, o Bling dispara webhook de volta. Pra evitar loop:

- Calcula hash do payload
- Se hash já foi processado nos últimos 60s, ignora
- Isso é feito em memoria com TTL

## Configuração

Variáveis estão em `.env`. Copie `.env.example` pra `.env` e edite se precisar mudar algo.

**Principais:**
- `DB_HOST` - Host do PostgreSQL
- `DB_PORT` - Porta PostgreSQL
- `DB_NAME` - Nome do banco
- `DB_USER` - Usuário PostgreSQL
- `DB_PASSWORD` - Senha PostgreSQL
- `JAVA_OPTS` - Opções JVM
- `SPRING_PROFILES_ACTIVE` - Profile (dev ou prod)

Pra mudar algo: Edite `.env` e rode `docker-compose up --build` de novo.

## Troubleshooting

### Porta 8080 já em uso?
```bash
# Edite .env:
SERVER_PORT=9090

docker-compose up --build
```

### PostgreSQL não conecta?
```bash
docker-compose logs postgres
docker-compose ps
```

### Limpar tudo e recomeçar?
```bash
docker-compose down -v
docker-compose up --build
```
