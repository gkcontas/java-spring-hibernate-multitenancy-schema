# Multi-tenancy na persistência: schema por tenant contra coluna discriminadora

Cinquenta tenants num PostgreSQL de verdade, a mesma aplicação de faturamento
escrita duas vezes — uma com um schema por tenant, outra com uma coluna
discriminadora — e as duas montadas com o suporte nativo do Hibernate. O assunto
não é fazer o isolamento funcionar. As duas funcionam. É onde cada uma vaza, o
que cada uma cobra, e quais das correções consagradas não corrigem nada.

Multi-tenancy é propriedade da `SessionFactory`, não de uma consulta nem de uma
entidade. Para ter as duas estratégias no mesmo processo são **duas unidades de
persistência** montadas à mão: dois `EntityManagerFactory`, dois gerenciadores de
transação, pacotes de entidades disjuntos, um único pool de conexões. Isso é
também a forma honesta do trade-off — quem precisa das duas paga pelas duas.

O instrumento é um `StatementInspector` que registra cada statement antes de ele
chegar ao driver, as estatísticas do próprio Hibernate e, onde a pergunta é sobre
plano, o `EXPLAIN (ANALYZE, BUFFERS)` do PostgreSQL. Nenhum número aqui é
estimado — e os tenants têm tamanhos diferentes de propósito, para que um
vazamento apareça como número errado em vez de exceção.

Seis resultados que mudam a conversa:

**Um `ThreadLocal` com tenant de fallback escreve na base de outro tenant e
responde 200.** O contexto do tenant é um thread local, que é correto para uma
requisição atendida do começo ao fim na thread da requisição e silencioso em todo
hand-off. Criar um cliente de globex dentro de um `ExecutorService` sem propagar
o contexto faz a linha **cair em `tenant_acme`**, que é o fallback configurado —
sem erro, sem log, sem nada. A mesma operação com a tarefa embrulhada cai em
`tenant_globex`. E **sem fallback nenhum** o mesmo bug é uma exceção **antes do
primeiro statement**: o Hibernate pede o tenant ao abrir a sessão, e não há
sessão sem resposta. Um default aqui não é conveniência, é a diferença entre um
erro e uma escrita cruzada.

**`search_path` sobrevive à devolução da conexão ao pool.** Uma conexão de pool
não é uma conexão nova: `search_path` é estado de sessão. Depois de uma única
transação de acme, **1 das 16 conexões do pool ainda aponta para `tenant_acme`**,
e um `select count(*) from customer` sem qualificação nessa conexão devolve **as
40 linhas de acme** — é o que qualquer `JdbcTemplate`, health check ou relatório
da mesma aplicação estaria fazendo. Com o reset no release: **0 de 16**, e a
mesma consulta falha com `relation "customer" does not exist`. Por isso o valor
neutro é `pg_catalog` e não `public`: um nome de tabela sem schema passa a falhar
alto em vez de resolver para o que estiver lá.

**`@TenantId` cobre tudo que o Hibernate escreve, e nada mais.** Rodando como
acme sobre a tabela compartilhada: a contagem em HQL devolve **40** com
`where tenant_id = ?` no SQL; o `findById` de um cliente de globex devolve
**vazio**, com o predicado somado ao load; um `update` em massa em HQL afeta
**60 linhas** e o SQL gerado carrega o predicado. A mesma contagem em **SQL
nativo devolve 532** — a tabela inteira, todos os tenants. O que o Hibernate
reescreve ele filtra; o que ele só repassa chega ao banco como foi escrito. A
coluna também é imutável: atribuir outro tenant a uma linha carregada não entra
no `update` gerado, e é o default certo.

**Uma unique key sem o tenant deixa um tenant tomar o número de nota do outro.**
`@TenantId` reescreve consultas; não escreve DDL. Com `unique (number)`, acme
toma `INV-1` e globex é **rejeitado por `invoice_naive_number_key`** — um tenant
bloqueando o namespace do outro. Com `unique (tenant_id, number)`, os dois
passam. A foreign key tem o problema espelhado: uma FK que só nomeia
`customer (id)` **aceita** uma nota de acme apontando para o cliente 41, que é de
globex; a FK composta **rejeita**. E a composta exige uma `unique (tenant_id,
id)` redundante no pai só para poder apontar. No schema do tenant as duas
garantias são de graça: não existe identificador que nomeie a linha de outro.

**O discriminador tem que liderar o índice, e há um índice por forma de
consulta.** As notas vencidas de um tenant, sobre um índice `(status, issued_on)`:
Bitmap Heap Scan, **5894 linhas lidas e descartadas por filtro, 556 buffers,
1,12 ms**. O mesmo sobre `(tenant_id, status, issued_on)`: Index Scan, **0
descartadas, 9 buffers, 0,26 ms** — 62x menos páginas. No schema do tenant, 7
buffers e 0,22 ms com o índice de duas colunas, porque a tabela já é do tenant.

**O aviso clássico sobre prepared statements e `search_path` não se reproduz no
PostgreSQL 16.** A história é que um statement preparado no servidor guarda o
plano, e `search_path` é resolvido na preparação, então o statement continuaria
respondendo do schema em que foi planejado. Medido numa conexão com
`prepareThreshold=2`, alternando `search_path` entre dois tenants de tamanhos
diferentes doze vezes, com `pg_prepared_statements` confirmando que **o statement
foi mesmo para o servidor**: **0 de 12 respostas vieram do schema errado**. O
PostgreSQL registra `search_path` como dependência do plano em cache e replaneja
quando ele muda. Escrever o schema no SQL continua sendo a garantia mais barata,
porque não depende disso.

## Tecnologias e bibliotecas

| Tecnologia | Para que serve aqui |
| --- | --- |
| Java 21 | aplicação |
| Spring Boot 3.5 | aplicação e API REST |
| Spring Data JPA | repositórios das duas unidades de persistência |
| Hibernate 6.6 | `MultiTenantConnectionProvider`, `CurrentTenantIdentifierResolver`, `@TenantId`, `Statistics` e `StatementInspector` |
| PostgreSQL 16 | `search_path` por tenant, índices compostos, chaves compostas e `EXPLAIN` |
| Flyway | schema compartilhado versionado no deploy, schemas de tenant versionados em runtime |
| HikariCP | o pool único que as duas estratégias dividem |
| Hibernate JCache + Caffeine | cache de segundo nível, e o cache da aplicação ao lado dele |
| Testcontainers | o PostgreSQL real nos testes de integração |
| JUnit 5 + AssertJ | 121 testes |

## Pré-requisitos

- Java 21
- Docker e Docker Compose

## Como rodar

```bash
docker compose up -d
```

```bash
./gradlew bootRun
```

Para derrubar tudo:

```bash
docker compose down -v
```

## Endpoints

Semear os 50 tenants nos dois modelos (é o dataset de onde vêm os números deste
README):

```bash
curl -s -X POST http://localhost:8080/api/dataset
```

Só os três tenants nomeados, para uma olhada rápida:

```bash
curl -s -X POST http://localhost:8080/api/dataset/named
```

Listar os tenants e o schema de cada um:

```bash
curl -s http://localhost:8080/api/tenants
```

Criar um tenant em runtime — cria o schema, roda todo o histórico de migrations
contra ele e registra, com os outros tenants sendo atendidos o tempo todo:

```bash
curl -s -X POST http://localhost:8080/api/tenants \
  -H 'Content-Type: application/json' \
  -d '{"tenantId":"newcomer","displayName":"Newcomer Ltd"}'
```

O faturamento. O tenant vem no header `X-Tenant-Id`, e nada abaixo do filtro
volta a mencioná-lo:

```bash
curl -s 'http://localhost:8080/api/invoices?status=PAID' -H 'X-Tenant-Id: acme'
curl -s 'http://localhost:8080/api/invoices/total?status=PAID' -H 'X-Tenant-Id: acme'
```

A mesma API sobre o modelo discriminador, para comparar as respostas:

```bash
curl -s 'http://localhost:8080/api/shared/invoices/total?status=PAID' -H 'X-Tenant-Id: acme'
```

A mesma contagem em SQL nativo, sem filtro, nomeada pelo que é:

```bash
curl -s 'http://localhost:8080/api/shared/invoices/count-across-tenants?status=PAID' \
  -H 'X-Tenant-Id: acme'
```

Sem o header, 400; com um tenant que não está no registro, 404:

```bash
curl -s 'http://localhost:8080/api/invoices/total?status=PAID'
curl -s 'http://localhost:8080/api/invoices?status=PAID' -H 'X-Tenant-Id: nobody'
```

Listar os cenários e a pergunta de cada um:

```bash
curl -s http://localhost:8080/api/scenarios
```

Rodar um cenário. `warmups` descarta execuções (a primeira paga JIT, plano do
Hibernate e plano do PostgreSQL) e `repeats` é o número de execuções
cronometradas, das quais se reporta a mediana:

```bash
curl -s -X POST 'http://localhost:8080/api/scenarios/index-leading-column?warmups=3&repeats=9'
```

Rodar todos, e ler os relatórios depois:

```bash
curl -s -X POST 'http://localhost:8080/api/scenarios?warmups=3&repeats=9'
curl -s http://localhost:8080/api/measurements
```

## O que cada variante produz

Cada relatório traz, por variante: statements preparados, entidades
materializadas, quantas linhas a variante devolveu — nenhuma variante pode
parecer barata respondendo uma pergunta menor — a mediana do tempo, o SQL que ela
provocou, colapsado por texto com a contagem de repetições, e um `outcome` em
texto.

Esse campo existe porque num projeto de multi-tenancy a resposta errada
normalmente não é mais lenta: é o número de outra pessoa, devolvido rapidamente.
O que o relatório tem que dizer é em qual schema a linha caiu, qual constraint
recusou, quantas conexões do pool estavam sujas.

As variantes são classificadas em quatro tipos: `PROBLEM` é o que sai do código
óbvio, `FIX` responde a mesma pergunta sem o problema, `TRAP` é o que parece
correção e não é, e `COST` não é certo nem errado — é o preço que a estratégia
cobra.

## As medições

50 tenants: três nomeados com tamanhos diferentes (acme com 40 clientes e 480
notas, globex com 15 e 120, initech com 7 e 35) e 47 de volume com 10 clientes e
1000 notas cada. **532 clientes e 47 635 notas por modelo**, e as notas do modelo
discriminador escritas duas vezes, na tabela com as constraints certas e na
tabela com as erradas. Carga completa em 12,0 s.

Jar empacotado rodando contra o PostgreSQL 16 do `docker-compose.yml`, daemon do
Gradle parado, uma execução completa de aquecimento descartada, 3 execuções de
aquecimento e a mediana de 9 cronometradas por variante. Tudo numa única sessão.

### `tenant-resolution` — um cliente criado para globex

| variante | | onde a linha caiu |
| --- | --- | --- |
| `request-thread` | correção | `tenant_globex` |
| `plain-executor` | problema | **`tenant_acme`** |
| `propagating-task` | correção | `tenant_globex` |
| `no-fallback` | correção | nenhuma: `TenantNotResolvedException` |

A quarta variante é a mesma tarefa no mesmo executor sem contexto, com o fallback
desligado. É a única configuração em que o bug aparece na primeira vez que roda.

### `search-path-leak` — o próximo tomador da conexão

| variante | | conexões sujas | `select count(*) from customer` |
| --- | --- | --- | --- |
| `no-reset` | problema | 1 de 16 | **40 linhas** (as de acme) |
| `reset-on-release` | correção | 0 de 16 | `relation "customer" does not exist` |

### `tenant-id-coverage` — rodando como acme sobre a tabela compartilhada

| variante | | statements | linhas | ms |
| --- | --- | --- | --- | --- |
| `hql-count` | correção | 1 | 40 | 0,55 |
| `find-by-id-other-tenant` | correção | 1 | 0 | 0,71 |
| `native-count` | problema | 1 | **532** | 0,26 |
| `bulk-hql-update` | correção | 2 | 60 | 2,32 |
| `insert-without-tenant-id` | correção | 1 | 1 | 1,28 |
| `move-row-to-another-tenant` | armadilha | 2 | 1 | 11,30 |

O load por identificador ganha o predicado junto com a chave:

```sql
select sc1_0.id,sc1_0.created_at,sc1_0.email,sc1_0.name,sc1_0.tenant_id
  from shared.customer sc1_0 where sc1_0.id=? and sc1_0.tenant_id = ?
```

O update em massa também, e é por isso que ele afeta 60 e não as 5954 linhas
vencidas que a tabela tem:

```sql
update shared.invoice si1_0 set status=? where si1_0.status=? and si1_0.tenant_id = ?
```

A última variante é a armadilha: atribuir outro tenant a uma linha carregada e
salvar. O `update` gerado é `set created_at=?,email=?,name=? where id=?` — sem
`tenant_id`. A atribuição fica na memória e nunca sai. Mover um tenant de lugar
é trabalho de SQL, não de mapeamento.

### `tenant-aware-constraints` — dois tenants, a mesma nota `INV-1`

| variante | | resultado |
| --- | --- | --- |
| `global-unique-number` | problema | rejeitado por `invoice_naive_number_key` |
| `composite-unique-number` | correção | aceito |
| `cross-tenant-foreign-key` | problema | **aceito**: nota de acme para o cliente 41, de globex |
| `composite-foreign-key` | correção | rejeitado por `invoice_customer_fk` |
| `schema-per-tenant-foreign-key` | correção | rejeitado por `invoice_customer_fk` |

As duas primeiras linhas são a mesma operação em duas tabelas que só diferem no
DDL — o mapeamento das entidades é idêntico, `@TenantId` incluído.

### `index-leading-column` — as notas vencidas de um tenant

| variante | | plano | descartadas por filtro | buffers | ms |
| --- | --- | --- | --- | --- | --- |
| `naive-index` | problema | Bitmap Heap Scan | **5894** | 556 | 1,12 |
| `tenant-leading-index` | correção | Index Scan | 0 | 9 | 0,26 |
| `schema-per-tenant` | correção | Bitmap Heap Scan | 0 | 7 | 0,22 |

As três devolvem as mesmas 60 notas. O índice errado é perfeitamente utilizável:
o PostgreSQL percorre as entradas de todos os tenants com aquele status e joga
fora as que não são de acme.

### `prepared-statement-plan` — uma conexão, dois tenants, o mesmo SQL

| variante | | preparados no servidor | respostas erradas |
| --- | --- | --- | --- |
| `server-side-prepare` | custo | 1 | 0 de 12 |
| `prepare-threshold-off` | correção | 0 | 0 de 12 |
| `schema-qualified-sql` | correção | 2 | 0 de 12 |

A terceira variante escreve o schema no SQL, o que dá a cada tenant um texto
diferente e, portanto, um plano próprio — são os dois statements preparados no
servidor. É a garantia que não depende do comportamento do cache de planos.

### `cache-key` — o total pago de acme, cacheado, e globex perguntando o seu

| variante | | acme | globex |
| --- | --- | --- | --- |
| `tenant-blind-key` | problema | 81 904 586 | **81 904 586** (o esperado era 17 241 316) |
| `tenant-in-key` | correção | 81 904 586 | 17 241 316 |
| `hibernate-entity-cache` | correção | 2 acertos de cache | o mesmo id devolve vazio |

Um cache acrescentado pela aplicação fica acima de tudo que sabe de tenant — o
resolver, o `search_path`, o predicado do `@TenantId`. Nada disso é consultado
num acerto. As chaves do cache de segundo nível do Hibernate já carregam o
identificador do tenant, então o mesmo id pedido por outro tenant dá miss e a
consulta devolve vazio.

### `cross-tenant-report` — o total pago de todos os 50 tenants

| variante | | statements | ms |
| --- | --- | --- | --- |
| `schema-loop` | custo | 50 | 28,86 |
| `generated-union-all` | correção | 1 | 5,91 |
| `discriminator-group-by` | correção | 1 | 8,56 |

É a pergunta em que o schema por tenant é ruim: o isolamento que deixa a consulta
de um tenant barata é o mesmo que transforma uma pergunta sobre todos em N
perguntas, e N é a lista de clientes. O `union all` traz de volta para um
statement ao preço de SQL gerado a partir do registro — 6937 caracteres com 50
ramos, que crescem e precisam ser reconstruídos a cada tenant novo.

### `migrations-and-catalog` — o que a quantidade de tenants custa

| variante | | o que foi medido | ms |
| --- | --- | --- | --- |
| `onboard-one-tenant` | custo | criar o schema e rodar todo o histórico | 46,51 |
| `sweep-every-tenant-schema` | custo | 50 schemas visitados, nenhuma migration nova | 426,03 |
| `migrate-shared-once` | correção | 1 schema, independente da quantidade de tenants | 5,79 |
| `catalog-relations` | custo | **600 relações em 50 schemas** contra 21 em `shared` | — |

Os 426 ms da varredura são o caso mais barato que existe: todos os schemas já na
versão corrente, nada para executar. O custo que ninguém orça é o catálogo — 12
relações por tenant entre tabelas, sequences, índices e constraints, e cada uma é
uma linha que outras sessões leem.

## Uma nota sobre `ddl-auto`

A unidade schema-por-tenant roda com `validate`, e isso valida **um** schema: o
que `getAnyConnection()` aponta. Não existe conexão que veja os cinquenta, e o
Hibernate pede uma conexão sem nomear tenant no bootstrap. O provider aponta essa
conexão para o primeiro tenant de bootstrap de propósito, para que a validação
aconteça; ela continua sendo uma afirmação sobre um tenant. A unidade
discriminadora não tem essa limitação — é um schema só.
