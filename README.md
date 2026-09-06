# The Barber — API de barbearia

Backend Java 21 / Spring Boot 4.0.8 com páginas HTML integradas, MySQL,
Spring Security, JPA/Hibernate, JWT, e-mail SMTP e checkout AbacatePay v1.

## Funcionalidades

| Perfil | Funcionalidades |
| --- | --- |
| Cliente | Cadastro, ativação por e-mail, login, edição de perfil e foto, busca de barbearias por localização, consulta de serviços/profissionais e horários, agendamento, cancelamento, negociação de reagendamento, checkout PIX/cartão e avaliação de atendimento concluído. |
| Estabelecimento | Login com código por e-mail, configuração de perfil, horários e galeria, cadastro de serviços e profissionais, agenda própria, fechamento/reabertura de dias, conclusão de atendimento e resposta a avaliações. |
| Administrador | Listagem de usuários, consulta de estabelecimentos pendentes, aprovação de estabelecimentos e acesso administrativo. O cadastro público não cria administradores. |

Estabelecimentos ativos, aprovados e com foto aparecem na busca de proximidade.
Clientes possuem limite de um agendamento ativo, ou três quando verificados.
Negociações pendentes também contam no limite. Dinheiro é permitido somente
após um atendimento concluído; a verificação é feita no servidor.
O envio de lembretes ocorre às 08h do fuso configurado no processo para a agenda do dia seguinte.

## Autenticação e permissões

O cadastro aceita `nome`, `email`, `senha`, `telefone` e `role`
(`CLIENTE` ou `ESTABELECIMENTO`). A senha exige pelo menos oito caracteres e no
máximo 72 bytes UTF-8, limite do BCrypt. Campos internos como `id`, aprovação,
avaliações e bloqueios não são copiados da requisição.

- `POST /api/usuarios`: cadastro inativo com código de ativação por e-mail.
- `POST /api/usuarios/login`: recebe `email` e `senha`.
- `POST /api/usuarios/validar-2fa`: recebe `email` e `codigo` de seis dígitos.
- `202`: é necessário validar o código; `206`: estabelecimento precisa completar
  o perfil; `200`: login concluído. As respostas autenticadas contêm `usuario` e `token`.
- Clientes ativos entram com senha; estabelecimentos passam pelo código a cada login.
- JWT válido por 24 horas, enviado em `Authorization: Bearer TOKEN`.
- Cinco falhas bloqueiam a conta por 15 minutos. Login e consumo do código utilizam
  bloqueio transacional da conta. Contas bloqueadas/inativas não autenticam pelo JWT.

CORS aceita apenas origens configuradas. A API não autentica por cookies; por isso
CSRF está desabilitado e CORS não habilita credenciais. Consultas de serviços
não expõem o usuário proprietário; terceiros veem apenas avaliações concluídas
na consulta da agenda de um estabelecimento.

## Principais rotas

| Método / rota | Uso e acesso |
| --- | --- |
| `GET /api/usuarios/{id}` | Próprio usuário ou administrador. |
| `GET /api/usuarios/estabelecimentos/proximos?lat=-30&lng=-51&raioKm=10` | Busca pública, raio limitado a 50 km. |
| `PUT /api/usuarios/{id}/completar-perfil` | Editar o próprio perfil. |
| `PUT /api/usuarios/{id}/foto`, `/galeria`, `/tags` | Foto própria; galeria e tags de estabelecimento próprio. |
| `GET /api/usuarios`, `/api/usuarios/estabelecimentos/pendentes` | Administração. |
| `PUT /api/usuarios/estabelecimentos/{id}/aprovar` | Aprovação administrativa. |
| `GET /api/servicos/estabelecimento/{id}` | Catálogo público. |
| `POST /api/servicos/estabelecimento/{id}` | Criar serviço no próprio estabelecimento. |
| `PUT`, `DELETE /api/servicos/{id}` | Alterar/excluir serviço próprio. |
| `GET`, `POST /api/barbeiros/estabelecimento/{id}` | Consultar equipe autenticado; criar apenas como proprietário. |
| `PUT`, `DELETE /api/barbeiros/{id}` | Editar nome ou desativar profissional próprio. |
| `GET /api/agendamentos` | Agenda filtrada pelo perfil; administrador vê todas. |
| `GET /api/agendamentos/cliente/{id}` | Agenda do próprio cliente. |
| `GET /api/agendamentos/estabelecimento/{id}` | Agenda própria; outros usuários recebem somente avaliações. |
| `GET /api/agendamentos/horarios-disponiveis` | Parâmetros `estabelecimentoId`, `servicoId`, `barbeiroId`, `data` (`YYYY-MM-DD`). |
| `POST /api/agendamentos` | Criar como cliente autenticado. |
| `PUT /api/agendamentos/{id}/status` | Cliente cancela; estabelecimento cancela ou conclui. |
| `PUT /api/agendamentos/{id}/propor-reagendamento` | Participante propõe `dataHoraProposta`; autor inferido da autenticação. |
| `PUT /api/agendamentos/{id}/confirmar-reagendamento` | A outra parte confirma a proposta. |
| `PUT /api/agendamentos/{id}/reagendar` | Alteração direta reservada ao administrador. |
| `PUT /api/agendamentos/{id}/avaliar` | Cliente avalia serviço concluído, nota 1–5 e comentário. |
| `PUT /api/agendamentos/{id}/responder-avaliacao` | Estabelecimento responde à avaliação própria. |
| `POST /api/agendamentos/estabelecimento/{id}/fechar-dia`, `/reabrir-dia` | Proprietário administra o dia informado em `data`. |
| `POST /api/agendamentos/{id}/pagar` | Cliente proprietário solicita checkout. |
| `DELETE /api/agendamentos/{id}` | Participantes excluem somente agendamento cancelado, sem cobrança e não pago. |
| `POST /api/webhooks/abacatepay` | Callback autenticado por secret e assinatura, sem JWT. |

Exemplo de criação de agendamento:

```json
{
  "dataHoraInicio": "2030-01-15T14:00:00",
  "estabelecimento": {"id": 2},
  "servico": {"id": 3},
  "barbeiro": {"id": 4},
  "formaPagamento": "PIX",
  "observacao": "Corte curto"
}
```

O cliente é obtido do JWT. Status, pagamento, avaliações e identificação da cobrança
são internos. O serviço e o profissional devem pertencer ao estabelecimento.
Serviços aceitam preço a partir de R$ 1,00, até duas casas decimais e duração de
1 a 720 minutos. Foto: PNG/JPEG/WebP Base64 ou URL HTTPS; galeria: até cinco imagens.

## Executar

1. Instale JDK 21 e MySQL 8. O Maven Wrapper está em `api/`.
2. Crie um banco vazio e um usuário exclusivo da aplicação.
3. Copie `api/.env.example` para `api/.env` e preencha as variáveis. Nunca versione segredos.
4. Para um banco **novo e descartável de desenvolvimento**, inicialize as tabelas:

```bash
cd api
bash mvnw spring-boot:run -Dspring-boot.run.arguments=--spring.jpa.hibernate.ddl-auto=update
```

Interrompa após a primeira inicialização e rode normalmente com validação do schema:

```bash
bash mvnw spring-boot:run
```

No Windows use `mvnw.cmd`. Acesse `http://localhost:8080/login.html`.
Para produção, gere/revise o schema e use migrações controladas; não habilite
`ddl-auto=update` indiscriminadamente. O antigo `schema.sql` era incompatível com
as entidades atuais e incluía contas de senha conhecida; não deve ser usado.

### Atualizar um banco existente

A nova versão exige `billing_id`, `billing_url` e `billing_amount` em `agendamentos`.
Com backup e a aplicação parada, aplique uma vez
[`api/migrations/001_payment_binding.sql`](api/migrations/001_payment_binding.sql)
sobre um banco compatível com as entidades anteriores. O script não converte o
antigo dump incompleto e não é executado automaticamente pelo Flyway.

Cobranças abertas antes da atualização não têm vínculo local: concilie-as no
provedor antes do corte. O webhook rejeita cobranças desconhecidas. Não gere uma
nova cobrança para substituir uma antiga sem verificar se já foi paga.

### Variáveis de ambiente

| Variável | Finalidade |
| --- | --- |
| `PORT` | Porta, padrão 8080. |
| `DB_URL`, `DB_USER`, `DB_PASSWORD` | Conexão MySQL. |
| `JWT_SECRET` | Chave aleatória com pelo menos 32 bytes; gere, por exemplo, com `openssl rand -base64 48`. |
| `MAIL_USER`, `MAIL_PASSWORD` | Conta SMTP Gmail e senha de aplicativo. |
| `FRONTEND_URL` | URL de retorno do checkout, por exemplo `https://seu-dominio/cliente.html`. |
| `CORS_ALLOWED_ORIGINS` | Origens exatas separadas por vírgula; padrão `http://localhost:8080`. |
| `ABACATEPAY_API_KEY` | Credencial do provedor. |
| `ABACATEPAY_WEBHOOK_SECRET` | Secret privado cadastrado na URL do webhook. |
| `ABACATEPAY_WEBHOOK_SIGNATURE_KEY` | Chave HMAC publicada pelo provedor; diferente do secret privado. |
| `ABACATEPAY_DEV_MODE` | `true` apenas para testes; padrão `false`. |
| `TZ` | Configure o processo, preferencialmente `America/Sao_Paulo`, para datas locais da agenda. |

Cadastre o callback como
`https://seu-dominio/api/webhooks/abacatepay?webhookSecret=SEU_SECRET`.
O servidor exige também `X-Webhook-Signature` em Base64 sobre o corpo bruto.
Consulte a [documentação de segurança](https://docs.abacatepay.com/pages/webhooks)
para obter a chave do provedor. Não registre a query string do webhook em logs de proxy.

A integração permanece na API v1: consome `billing.paid`, com cobrança em `data`
ou `data.billing`. Confere ID, valor em centavos e `devMode`; callbacks repetidos
não duplicam a atualização nem reabrem agendamentos. Cancelamento, falha e estorno
não são usados para cancelar automaticamente a agenda. Eles exigem conciliação.
O checkout exige dados reais de contato e documento, sem CPF ou telefone fictícios.

## Testes e estrutura

```bash
cd api
bash mvnw verify
```

Os testes usam H2 em memória, sem credenciais reais, e mocks nas integrações.
A execução de CI usa Java 21. Testes com MySQL e callbacks reais de homologação
continuam necessários antes de produção.

- `controllers/`: HTTP e autorização dos recursos.
- `dto/`: contratos permitidos para cadastro, login, código e novo agendamento.
- `services/`: autenticação, agenda, pagamento, validação de mídia e webhook.
- `security/`: JWT, filtros e política HTTP.
- `repositories/`, `models/`: persistência e domínio.
- `docs/SECURITY_AUDIT.md`: achados, correções e limites da auditoria.

## Limitações relevantes

As páginas ainda utilizam `innerHTML`, JavaScript inline e JWT no `localStorage`.
A CSP mantém `unsafe-inline` por compatibilidade; o frontend requer uma revisão
específica de XSS e codificação de saída. Não há garantia de proteção completa
contra XSS. Estornos, reconciliação de pagamentos,
limites de tráfego globais e testes de concorrência em MySQL precisam evoluir.
Esta revisão não constitui certificação de segurança.
