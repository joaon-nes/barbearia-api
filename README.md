# 💈 The Barber® - Booking Platform

![Java](https://img.shields.io/badge/Java-17+-ED8B00?style=for-the-badge&logo=java&logoColor=white)
![Spring Boot](https://img.shields.io/badge/Spring_Boot-3.x-6DB33F?style=for-the-badge&logo=spring&logoColor=white)
![MySQL](https://img.shields.io/badge/MySQL-00000F?style=for-the-badge&logo=mysql&logoColor=white)
![Bootstrap](https://img.shields.io/badge/Bootstrap-5-563D7C?style=for-the-badge&logo=bootstrap&logoColor=white)
![JavaScript](https://img.shields.io/badge/JavaScript-F7DF1E?style=for-the-badge&logo=javascript&logoColor=black)

The Barber® is a **complete monolithic** system (integrated backend and frontend) developed to improve how customers find and schedule services at barbershops. The system features real-time geolocation, integrated payments, and a high-standard security architecture.

## Key Features

### For the Client
* **Smart Geolocation:** Find the nearest barbershops using device GPS or ZIP code search (integrated with ViaCEP and Nominatim/Leaflet).
* **Real-Time Booking:** Dynamic calculation of available slots based on service duration and barber schedule.
* **Online Payments:** Secure checkout via PIX or Credit Card integrated with the **AbacatePay** API.
* **Review System:** Star rating (1 to 5) and real feedback restricted to completed services.
* **2FA:** Two-factor authentication via Email for account security.

### For the Establishment (Barbershop)
* **Profile Management:** Set operating hours, amenities, photo gallery, and social media links.
* **Team Management:** Manage multiple barbers and custom service menus.
* **Appointment Control:** Handle rescheduling requests and track financial status.

### For the Administrator (Platform)
* **Dashboard:** Global overview with metrics for total clients and active partners.
* **KYB Flow (Know Your Business):** Manual approval of new barbershops before they go public to prevent fake profiles and fraud.

---

## Architecture & Security

The project follows strict security standards based on the **OWASP Top 10**:
* **API Shielding:** Stateless authentication via **JWT (JSON Web Tokens)** with 256-bit keys.
* **Attack Prevention:** Rigorous **CORS** and **CSP (Content Security Policy)** settings.
* **Concurrency Control:** Protection against *Race Conditions* and *Overbooking* using pessimistic database locking.
* **Data Sanitization:** Active protection against *Stored XSS* in all user inputs.
* **Rate Limiting:** Automatic account lockout after consecutive failed login attempts (Brute Force protection).
* **Secret Management:** Zero credentials in the source code, using 100% injection via environment variables (`.env`).

---

## Tech Stack

* **Backend:** Java 17, Spring Boot, Spring Security, Spring Data JPA, Hibernate.
* **Frontend:** HTML5, CSS3, Vanilla JavaScript, Bootstrap 5, Leaflet (Interactive Maps).
* **Database:** MySQL.
* **Integrations:** AbacatePay (Payment Gateway), ViaCEP, Nominatim/OpenStreetMap, JavaMailSender.

---

## How to Run Locally

### 1. Prerequisites
* Java 17+ installed.
* Maven installed.
* MySQL Server running on port 3306.

### 2. Environment Variables Setup
Create a `.env` file in the project root (same directory as `pom.xml`) using `.env.example` as a template:

```env
PORT=8080
DB_URL=jdbc:mysql://localhost:3306/barbearia
DB_USER=root
DB_PASSWORD=your_local_password
JWT_SECRET=generate_a_secure_256_bit_key_here
ABACATEPAY_API_KEY=sk_test_your_test_key
MAIL_USER=your_email@gmail.com
MAIL_PASSWORD=your_google_app_password
FRONTEND_URL=http://localhost:8080/cliente.html
```

# 💈 The Barber® - Plataforma de Agendamentos

![Java](https://img.shields.io/badge/Java-17+-ED8B00?style=for-the-badge&logo=java&logoColor=white)
![Spring Boot](https://img.shields.io/badge/Spring_Boot-3.x-6DB33F?style=for-the-badge&logo=spring&logoColor=white)
![MySQL](https://img.shields.io/badge/MySQL-00000F?style=for-the-badge&logo=mysql&logoColor=white)
![Bootstrap](https://img.shields.io/badge/Bootstrap-5-563D7C?style=for-the-badge&logo=bootstrap&logoColor=white)
![JavaScript](https://img.shields.io/badge/JavaScript-F7DF1E?style=for-the-badge&logo=javascript&logoColor=black)

The Barber® é um sistema **monolítico completo** (Backend + Frontend integrados) desenvolvido para melhorar a forma como os clientes encontram e agendam serviços em barbearias. O sistema utiliza geolocalização em tempo real, pagamentos integrados e uma arquitetura robusta de segurança.

## Principais Funcionalidades

### Para o Cliente
* **Geolocalização Inteligente:** Encontra as barbearias mais próximas utilizando o GPS do telemóvel ou pesquisa por CEP (integração com ViaCEP e Nominatim/Leaflet).
* **Agendamento em Tempo Real:** Cálculo dinâmico de horários disponíveis com base na duração do serviço e na agenda do barbeiro.
* **Pagamentos Online:** Pagamento seguro via PIX ou Cartão de Crédito integrado com a API do **AbacatePay**.
* **Sistema de Avaliações:** Classificação por estrelas (1 a 5) e comentários reais apenas para serviços concluídos.
* **2FA:** Autenticação de dois fatores via E-mail.

### Para o Estabelecimento (Barbearia)
* **Gestão de Perfil:** Configuração de horários de funcionamento, comodidades, galeria de fotos e links sociais.
* **Gestão de Equipa:** Cadastro de múltiplos barbeiros e serviços personalizados.
* **Controle de Agenda:** Aceitação de remarcações sugeridas e acompanhamento do status financeiro.

### Para o Administrador (Plataforma)
* **Painel de Controle:** Visão global com métricas de clientes e parceiros.
* **Fluxo KYB (Know Your Business):** Aprovação manual de novas barbearias antes de estas serem listadas publicamente, evitando perfis fantasma e fraudes.

---

## Arquitetura e Segurança

O projeto segue estritas normas de segurança baseadas no **OWASP Top 10**:
* **Blindagem de APIs:** Autenticação *Stateless* via **JWT (JSON Web Tokens)** com chaves de 256-bits.
* **Prevenção de Ataques:** Políticas rigorosas de **CORS** e **CSP (Content Security Policy)** configuradas no Spring Security.
* **Controle de Concorrência:** Proteção contra *Race Conditions* e *Overbooking* (marcações duplas) utilizando bloqueios transacionais no banco de dados.
* **Sanitização de Dados:** Proteção ativa contra *Stored XSS* em todos os inputs de utilizador.
* **Rate Limiting:** Bloqueio automático de contas após sucessivas tentativas de login falhadas (Proteção contra Brute Force).
* **Gestão de Segredos:** Ausência de credenciais no código-fonte, utilizando injeção total via variáveis de ambiente (`.env`).

---

## Tecnologias Utilizadas

* **Backend:** Java 17, Spring Boot, Spring Security, Spring Data JPA, Hibernate.
* **Frontend:** HTML5, CSS3, Vanilla JavaScript, Bootstrap 5, Leaflet (Mapas interativos).
* **Banco de Dados:** MySQL.
* **Integrações (APIs):** AbacatePay (Gateways de Pagamento), ViaCEP, Nominatim/OpenStreetMap, JavaMailSender.

---

## Como Executar Localmente

### 1. Pré-requisitos
* Java 17+ instalado.
* Maven instalado.
* Servidor MySQL a rodar na porta 3306.

### 2. Configuração das Variáveis de Ambiente
Crie um ficheiro `.env` na raiz do projeto (mesmo diretório do `pom.xml`) utilizando o `.env.example` como base:

```env
PORT=8080
DB_URL=jdbc:mysql://localhost:3306/barbearia
DB_USER=root
DB_PASSWORD=sua_senha_local
JWT_SECRET=gere_uma_chave_segura_aqui_com_256_bits
ABACATEPAY_API_KEY=sk_test_sua_chave_de_teste
MAIL_USER=seu_email@gmail.com
MAIL_PASSWORD=sua_senha_de_app_do_google
FRONTEND_URL=http://localhost:8080/cliente.html