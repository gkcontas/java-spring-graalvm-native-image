# Imagem nativa com GraalVM

Uma API Spring Boot comum — JPA, Flyway, validação, Actuator, Postgres —
compilada antecipadamente para um executável nativo com GraalVM e comparada,
com números, contra a mesma aplicação na JVM. O projeto mede o que a imagem
nativa entrega (boot e memória), o que ela cobra (throughput no pico, tempo e
memória de build) e mostra o que deixa de funcionar quando a aplicação passa a
viver num **mundo fechado**: reflexão, recursos, proxies e serialização que
ninguém declarou, e condições do Spring congeladas no momento da compilação.

## Tecnologias

- **Java 21** e **Spring Boot 3.5** com **Spring AOT**
- **GraalVM Native Image** (Community 21) via plugin
  `org.graalvm.buildtools.native` do Gradle
- **Spring Data JPA** / **Hibernate** e **Flyway** sobre **PostgreSQL 16**
- **Docker** multi-stage: o binário é compilado dentro da imagem
  `ghcr.io/graalvm/native-image-community:21` e roda sobre
  `distroless/base-debian12` — a máquina só precisa de Docker
- **Testcontainers** nos testes de integração
- **Gradle** (Kotlin DSL) com wrapper

## A aplicação

Um catálogo de produtos pequeno, só para haver trabalho real a fazer:

| Método | Rota | O que faz |
| --- | --- | --- |
| `POST` | `/api/products` | cria um produto (sku único, preço > 0) |
| `GET` | `/api/products/{id}` | lê um produto |
| `GET` | `/api/products?category=&limit=` | lista uma categoria em ordem de id |
| `GET` | `/api/products/{id}/quote?region=` | preço com imposto da região |
| `GET` | `/api/diagnostics` | modo de execução e as sondas do mundo fechado |
| `POST` | `/api/admin/seed?count=` | gera produtos dentro do Postgres |

A cotação foi desenhada com duas coisas que a análise estática não enxerga: a
tabela de alíquotas é um **recurso** (`pricing/tax-rates.json`) lido com
Jackson para um `record`, e a estratégia de arredondamento é uma **classe
escolhida por nome** na configuração (`app.pricing.rounding`), instanciada com
`Class.forName`. As duas funcionam na JVM sem esforço nenhum, e as duas
derrubam o binário nativo na subida se ninguém declarar os hints.

## O mundo fechado

A compilação nativa parte do `main` e inclui só o que consegue provar que é
alcançável. O que é decidido em tempo de execução — um nome de classe vindo de
configuração, um caminho de recurso, uma interface para proxy, um tipo que o
Jackson descobre por reflexão — fica de fora, a não ser que exista um *hint*.
O Spring AOT gera boa parte desses hints sozinho (beans, controllers, tipos de
`@RequestBody` e de retorno, entidades JPA), e o repositório de metadados do
GraalVM cobre bibliotecas como o driver do Postgres e o Logback. O que é
específico da aplicação fica com a aplicação.

O endpoint `/api/diagnostics` tenta cada tipo de acesso dinâmico duas vezes,
com hint e sem hint. Os nomes de classe vêm do `application.yml` para o
compilador não conseguir transformá-los em referência direta — é a situação de
um plugin ou de uma estratégia configurável em produção.

| Sonda | Com hint | Sem hint, na JVM | Sem hint, nativo |
| --- | --- | --- | --- |
| classe por nome | ok | ok | `ClassNotFoundException` |
| recurso de classpath | ok | ok | `FileNotFoundException` |
| proxy JDK | ok | ok | `MissingReflectionRegistrationError` |
| Jackson em tipo avulso | ok | ok | `InvalidDefinitionException: No serializer found` |

A coluna do meio é a armadilha: na JVM tudo passa, a suíte de testes fica
verde e o erro só aparece no binário — em produção, se ninguém rodar o nativo
antes. Duas observações de entrevista:

- o caso do Jackson poderia nem lançar exceção: com
  `FAIL_ON_EMPTY_BEANS` desligado, ele escreve `{}` em silêncio. A sonda compara
  o JSON produzido com o esperado em vez de confiar na ausência de erro;
- o proxy e a reflexão em metadados ausentes podem aparecer como `Error`, não
  como `Exception` — um `catch (Exception e)` não pega.

### Os três jeitos de declarar

- `RuntimeHintsRegistrar` + `@ImportRuntimeHints`: código, cobre tudo
  (reflexão, recurso, proxy, serialização). É o que `PricingHints` e
  `DiagnosticsHints` usam.
- `@RegisterReflectionForBinding(Tipo.class)`: atalho para tipos que o Jackson
  vai ler ou escrever. Usado no `HintedPayload`.
- Arquivos `META-INF/native-image/.../reflect-config.json` escritos à mão, ou
  gerados pelo *tracing agent* rodando a aplicação na JVM. Úteis para
  bibliotecas de terceiros; para código próprio, o registrar é testável.

### Testar hints sem compilar nativo

Compilar o binário leva minutos. Os hints são testados na JVM, em
milissegundos, com `RuntimeHintsPredicates`: o teste confirma que o recurso,
o tipo de binding e cada estratégia de arredondamento estão registrados — e
que os quatro "sem hint" **não** estão, para a demonstração não se corrigir
sozinha. Um dos testes varre o classpath atrás de toda implementação de
`RoundingStrategy` e exige um hint para cada uma: quem criar uma estratégia
nova e esquecer o registrar quebra o build da JVM, não a produção.

## Condições congeladas no build

O Spring AOT avalia `@ConditionalOnProperty`, `@ConditionalOnClass`,
`@Profile` e afins **durante a compilação** e grava o resultado como código.
No binário, ligar a propriedade não cria o bean; `@Value` continua sendo lido
em tempo de execução. A aplicação tem um bean `AuditTrail` condicionado a
`app.audit.enabled` (falso no `application.yml`), e foi iniciada com
`--app.audit.enabled=true --app.greeting=hola`:

| Modo | propriedade em runtime | bean existe | `@Value` greeting |
| --- | --- | --- | --- |
| JVM | `true` | sim | `hola` |
| JVM com `-Dspring.aot.enabled=true` | `true` | **não** | `hola` |
| nativo | `true` | **não** | `hola` |

A linha do meio é a forma barata de encontrar esse problema: o mesmo jar,
rodando com o código gerado pelo AOT, reproduz o comportamento do nativo sem
compilar nada. Consequência prática: perfis e feature flags que trocam beans
precisam ser decididos no build (uma imagem por variante) ou virar lógica
dentro do bean.

## As medições

Setup: Intel i7-8700 (6 núcleos, 12 threads), 23 GB de RAM, Debian 12. Tudo
medido numa única sessão, com o jar empacotado (`bootJar`) e o binário
extraído da imagem, rodando direto no host, Gradle daemon parado. A aplicação
fica presa em 4 núcleos (`taskset -c 0-3`) e o gerador de carga nos outros 8,
para não competirem; heap limitado a `-Xmx512m` nos quatro modos. JVM:
Corretto 21.0.5; nativo: GraalVM Community 21.0.2, Serial GC, sem PGO.
Postgres do `docker-compose.yml` com 100 mil produtos.

Os quatro modos:

- **JVM**: `java -jar`;
- **JVM + AOT**: o mesmo jar com `-Dspring.aot.enabled=true`, usando as
  definições de bean geradas no build em vez de escanear e refletir na subida;
- **JVM + AOT + CDS**: jar extraído (`-Djarmode=tools extract`) e um arquivo
  de *Class Data Sharing* gerado numa execução de treino
  (`-XX:ArchiveClassesAtExit`, `-Dspring.context.exit=onRefresh`);
- **nativo**: o executável.

### Boot e memória em repouso

Mediana de 7 subidas. "Pronto" é o tempo do processo iniciado até o primeiro
200 em `/actuator/health`, incluindo Hikari, Flyway e Hibernate contra o banco.

| Modo | pronto | "Started in" do Spring | RSS em repouso |
| --- | ---: | ---: | ---: |
| JVM | 4 115 ms | 3 666 ms | 312 MB |
| JVM + AOT | 3 719 ms | 3 253 ms | 309 MB |
| JVM + AOT + CDS | 1 642 ms | 1 357 ms | 275 MB |
| nativo | **222 ms** | 214 ms | **176 MB** |

O AOT sozinho ganha pouco (−10%): ele tira a avaliação de condições e a
reflexão de configuração, mas o grosso do boot é carregar e verificar classes.
O CDS ataca justamente isso e corta 60% sem abrir mão do JIT. O nativo é 18
vezes mais rápido que a JVM pura e 7 vezes mais que AOT + CDS.

### Sob carga

`GET /api/products/{id}/quote?region=SP` com ids aleatórios, 16 conexões em
laço fechado. Primeiro as 2 000 requisições logo depois da subida (código
frio), depois 45 s de aquecimento descartados e 30 s medidos.

| Modo | 2 000 primeiras: req/s | p50 | p99 | estável: req/s | p50 | p99 | RSS após carga |
| --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: |
| JVM | 903 | 13,8 ms | 63,6 ms | 11 892 | 1,21 ms | 3,39 ms | 511 MB |
| JVM + AOT | 865 | 14,8 ms | 79,3 ms | 11 858 | 1,20 ms | 3,43 ms | 473 MB |
| JVM + AOT + CDS | 864 | 15,0 ms | 65,9 ms | 12 028 | 1,18 ms | 3,56 ms | 525 MB |
| nativo | **3 009** | **4,3 ms** | **38,8 ms** | 7 482 | 1,76 ms | 6,57 ms | **181 MB** |

As duas metades da tabela contam histórias opostas:

- **Frio**, o nativo responde 3,3 vezes mais: o código já está compilado,
  enquanto a JVM interpreta e compila em paralelo. CDS e AOT não ajudam aqui —
  eles aceleram o carregamento de classes, não o JIT.
- **Aquecido**, o JIT vence: o nativo entrega 63% do throughput e o dobro de
  p99. O C2 otimiza com o perfil real da execução; o compilador antecipado da
  edição Community não tem esse perfil (PGO é recurso do Oracle GraalVM) e o
  Serial GC é o único coletor disponível.
- A memória continua do lado do nativo: 181 MB contra 473–525 MB, com o mesmo
  limite de heap. Na JVM pesam metaspace, code cache e o próprio compilador.

### Tamanho e build

| | JVM | nativo |
| --- | ---: | ---: |
| artefato | jar 58,0 MB | executável 165,8 MB |
| imagem de container | 394 MB (`eclipse-temurin:21-jre`, base de 336 MB) | 187 MB (`distroless/base`, base de 21 MB) |
| build | `clean bootJar`: 23 s | `native-image`: 2 min 44 s, 6,8 GB de pico de RSS (4 min 31 s o `docker build` inteiro) |

O executável é maior que o jar porque carrega o heap pré-inicializado e o
código de máquina de tudo que é alcançável — inclusive a parte do JDK. A
imagem ainda sai com metade do tamanho porque não leva JRE. O custo mais
sentido no dia a dia é o build: sete vezes mais lento e com memória que um
runner de CI padrão nem tem. O ciclo de desenvolvimento continua na JVM, e o
nativo vira uma etapa do pipeline.

## Quando compensa

**Compensa** quando a instância vive pouco ou fica parada: *scale-to-zero*,
funções, jobs curtos, CLIs, sidecars, e quando a cobrança é por memória — 176
MB contra 312 MB em repouso, numa frota de muitas réplicas pequenas, é
dinheiro.

**Não compensa** num serviço de vida longa com carga sustentada: depois de
aquecida, a JVM processou 59% a mais com metade da latência de cauda. Se o
problema é só boot, **AOT + CDS** na JVM já entrega 60% do ganho sem mundo
fechado, sem hints e sem build de minutos.

E em qualquer caso: toda biblioteca e todo acesso dinâmico precisam ser
compatíveis, e a única prova é rodar o binário — os testes na JVM não pegam
o que falta.

## Como rodar

```bash
export DOCKER_HOST=unix:///var/run/docker.sock   # se o contexto padrão não responder
docker compose up -d --wait
./gradlew bootRun
```

Imagem nativa (só precisa de Docker; leva alguns minutos e alguns GB de RAM):

```bash
docker build -f docker/Dockerfile.native -t native-image-catalog:native .
docker run --rm --network host native-image-catalog:native
```

Só o executável, para rodar no host:

```bash
docker build -f docker/Dockerfile.native --target binary --output build/native .
./build/native/native-image-catalog
```

Testes (sobem um Postgres com Testcontainers):

```bash
./gradlew test
```

Medições (Postgres de pé, jar e binário construídos, daemon do Gradle parado):

```bash
python3 scripts/measure.py
```

## Exemplos

```bash
curl -s -X POST localhost:8080/api/products -H 'Content-Type: application/json' \
  -d '{"sku":"BOOK-1","name":"Clean Code","category":"books","price":0.25}'

curl -s 'localhost:8080/api/products/1/quote?region=SP'
# {"productId":1,"sku":"BOOK-1","region":"SP","net":0.25,"taxRate":0.18,
#  "tax":0.04,"gross":0.29,"rounding":"HalfEvenRounding","tableVersion":"2026-11"}

curl -s 'localhost:8080/api/products?category=books&limit=10'

curl -s localhost:8080/api/diagnostics
```

O imposto de `0,25 × 0,18 = 0,045` é um empate: o arredondamento bancário
(`HALF_EVEN`, o padrão) fica em `0,04`; trocar `app.pricing.rounding` para
`HalfUpRounding` dá `0,05`.

## Estrutura

```
src/main/java/com/gkcontas/nativeimage
├── product/       entidade, repositório, serviço e o bean condicional de auditoria
├── pricing/       cotação, tabela em recurso, estratégias por nome e PricingHints
├── diagnostics/   sondas com e sem hint e DiagnosticsHints
└── api/           controllers, DTOs e tratamento de erros
docker/            Dockerfile.native (multi-stage GraalVM) e Dockerfile.jvm
scripts/           measure.py e LoadGen.java (gerador de carga em laço fechado)
```
