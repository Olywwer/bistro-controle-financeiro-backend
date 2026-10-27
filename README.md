# Bistrô de Sabores — API Financeira

API Java 21 com Spring Boot e PostgreSQL. Os dados financeiros ficam no banco, não no navegador.

## Publicar no Render com Supabase

1. Crie um projeto Supabase. Na opção **Connect**, selecione **Session pooler** e anote o host, a porta, o banco e o usuário exibidos.
2. No Render, crie um Blueprint pelo repositório backend e use o `render.yaml` desta pasta. O serviço usa o plano gratuito na região Virginia.
3. No formulário de variáveis protegidas do Blueprint, configure:
   - `DATABASE_URL`: `jdbc:postgresql://HOST:PORTA/NOME_DO_BANCO?sslmode=require`
   - `DATABASE_USERNAME` e `DATABASE_PASSWORD`: credenciais do Session pooler exibidas pelo Supabase.
   - `FRONTEND_ORIGIN`: origem HTTPS do frontend publicada no Render, sem barra final.
4. Aguarde o deploy e teste `https://URL-DA-API/api/health`; a resposta esperada é `{"status":"ok"}`.

O Session pooler é compatível com redes IPv4 e com conexões persistentes. O Blueprint ativa HTTPS nos cookies e permite chamadas com sessão somente da origem configurada do frontend. Não compartilhe as credenciais nem as coloque em arquivos versionados; informe-as somente no painel do Render.

O serviço gratuito do Render pode suspender a API quando estiver ociosa, tornando a primeira solicitação mais lenta. Confira também os limites e as condições atuais do plano Supabase escolhido.

## Executar localmente

1. Instale o JDK 21 e o PostgreSQL.
2. Copie `.env.example` para `.env` e preencha as credenciais do banco.
3. Abra `pom.xml` no IntelliJ IDEA como projeto Maven e execute `FinanceiroApiApplication.java`, ou execute `mvn spring-boot:run`.

A API local fica em `http://127.0.0.1:3001`. A consulta SQL de lançamentos está em `consultar-lancamentos.sql`.
