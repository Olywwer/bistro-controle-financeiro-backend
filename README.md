# Bistrô de Sabores — Backend

API REST em Java 21 e Spring Boot para o sistema financeiro do Bistrô de Sabores. Os dados ficam no PostgreSQL; configure as credenciais localmente e nunca versione o arquivo `.env`.

## Configuração

1. Instale o JDK 21 e o PostgreSQL.
2. Crie o banco e o usuário da aplicação no PostgreSQL.
3. Copie `.env.example` para `.env` e preencha `DATABASE_URL`, `DATABASE_USERNAME` e `DATABASE_PASSWORD` com os dados do seu ambiente.
4. Abra este repositório no IntelliJ IDEA 2025.2.5 como um projeto Maven e selecione o JDK 21.

## Executar

Execute `FinanceiroApiApplication.java` pelo IntelliJ ou, com Maven instalado, use `mvn spring-boot:run`.

A API fica em `http://127.0.0.1:3001`. Verifique a inicialização em `http://127.0.0.1:3001/api/health`, que deve responder `{"status":"ok"}`. O frontend está no [repositório frontend](https://github.com/Olywwer/bistro-controle-financeiro-frontend) e encaminha as chamadas `/api` para essa porta.

## Consultar lançamentos

O arquivo `consultar-lancamentos.sql` lista os lançamentos e os respectivos dados de autoria armazenados no PostgreSQL. Execute-o no banco configurado para a aplicação.
