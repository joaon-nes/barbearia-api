# Auditoria do backend — 5 de setembro de 2026

Base revisada: `a11eb014e469ddba64fd62e670ff8f8238310679`.
Escopo: código do backend, configuração, serialização, integrações e dependências.
Não houve acesso a banco, SMTP, infraestrutura ou credenciais de produção.
As severidades abaixo são avaliações técnicas locais, não pontuações CVSS.

## Achados corrigidos

| Severidade | Achado e impacto | Correção |
| --- | --- | --- |
| Alta | Cadastro recebia uma entidade persistente, inclusive ID e estado interno, permitindo merge de dados e adulteração de atributos. | DTO de cadastro cria sempre uma entidade nova; somente campos permitidos são copiados; ADMIN é rejeitado. |
| Alta | Agendamento aceitava campos de avaliação e negociação controlados pelo servidor. | DTO de criação; cliente derivado da autenticação; pagamento e avaliações não vêm do corpo. |
| Alta | Serviço/profissional de outra barbearia podiam ser associados ao agendamento. | Validação de pertencimento, atividade do profissional e aprovação/atividade do estabelecimento. |
| Alta | Autor da proposta de reagendamento era fornecido pelo cliente; a alteração direta também contornava o aceite. | Autor derivado da conta, alteração direta restrita a ADMIN e endpoint de status limitado a concluir/cancelar. |
| Alta | Consulta pública de serviços serializava o estabelecimento completo, incluindo dados pessoais. | Relação com proprietário excluída da serialização; CPF/e-mail/CNPJ também removidos das relações na agenda. |
| Média | Terceiros conseguiam consultar horários, IDs e nomes na agenda de outro estabelecimento. | Retorno de terceiros limitado a avaliações de atendimentos concluídos, sem identificação ou horários de reserva. |
| Alta | Webhook confundia secret privado com chave de assinatura, sem vínculo persistido à cobrança e sem checagem de valor/ambiente. | Secret privado e assinatura independentes; cobrança vinculada ao checkout; verificação de valor e ambiente. |
| Alta | Webhook repetido podia reabrir agenda cancelada/concluída ou reverter pagamento. | Atualização idempotente sob bloqueio; pagamento não modifica o estado da agenda. Somente evento v1 de pagamento é processado. |
| Média | Cobrança usava CPF/telefone fictícios e arredondamento/truncamento implícito. | Exigência de dados informados, centavos exatos, limite mínimo validado, timeout HTTP e reaproveitamento do checkout persistido. |
| Média | Exclusão apagava histórico de cobranças. | Exclusão somente de agenda cancelada, sem cobrança associada e não paga. |
| Média | CORS permitia qualquer origem com credenciais. | Origens exatas configuráveis e credenciais desabilitadas; autenticação Bearer. |
| Média | Códigos sem expiração podiam ser aceitos; consumo e tentativas não tinham transação conjunta. | Expiração obrigatória; autenticação extraída em serviço transacional com bloqueio por conta. |
| Média | Bloqueio do cliente não impedia dois clientes de reservar o mesmo profissional simultaneamente. | Bloqueio do profissional na criação e nas alterações de horário, com READ_COMMITTED para enxergar reservas confirmadas após a espera; registros de agenda bloqueados nas mutações. Teste concorrente com MySQL permanece necessário. |
| Média | Duração zero e avanço de LocalTime atravessando meia-noite podiam causar loop no cálculo de slots. | Validação de 1–720 minutos e iteração com data/hora completa. |
| Média | Foto/galeria usavam substituição textual como validação de URL. | Lista permitida de esquemas/formatos, rejeição de aspas/markup, limites por imagem e galeria. |
| Média | Limite de agendamentos ignorava negociações e dinheiro dependia da interface. | Negociações incluídas; elegibilidade de dinheiro conferida pelo histórico no servidor. |
| Média | SDK não utilizado dependia de `main-SNAPSHOT` via JitPack. | Dependência e repositório removidos; Spring Boot atualizado de 4.0.4 para 4.0.8. |
| Média | Dump incluía DROP TABLE e senhas de exemplo conhecidas, além de schema incompatível. | Dump substituído por orientação sem SQL destrutivo; migração incremental separada para vínculo de pagamentos. |

Outros ajustes: validação de coordenadas, DTOs de login/código, JWT interpretado uma
vez por requisição com UTF-8 explícito, autoridades obtidas da conta atual, Actuator
restrito ao administrador, tratamento de entrada inválida e conflito sem detalhes
SQL, timeouts de SMTP e exigência de STARTTLS.

## Dependências

A consulta OSV dos 110 componentes Maven de escopo compile/runtime encontrou três
alertas em `tomcat-embed-core:11.0.24` após atualizar o Spring Boot:
`GHSA-9xv2-5v5q-p794`, `GHSA-gcx9-497g-6cp6` e `GHSA-h3x4-894j-xpx5`.
Tomcat foi fixado em `11.0.25`, versão com correções publicadas pela Apache.
A presença do componente afetado não comprova exploração nesta configuração.
A nova consulta ao OSV em 05/09/2026 não retornou alertas para os 110 componentes
compile/runtime resolvidos. Isso é uma fotografia da base consultada, não garantia
de ausência de vulnerabilidades; dependências de teste, plugins de build e frontend
não integram essa contagem.
O override deve ser reavaliado quando o BOM do Spring Boot incorporar a correção.

Referência: [avisos oficiais do Tomcat 11](https://tomcat.apache.org/security-11.html).

## Validação

Suite de regressão com H2 em memória, MockMvc/Spring Security e mocks nas integrações.
Cobre cadastro, controle de campos internos, expiração de código, CORS, rotas privadas,
assinatura, valor/ambiente de webhook, idempotência, reagendamento, serialização,
imagens, isolamento entre estabelecimentos e término do cálculo de slots.

Comando: `cd api && bash mvnw verify`, com Java 21. Resultado: 15 testes, sem falhas ou erros.
H2 não reproduz integralmente locks, collation e DDL do MySQL. A suite não chama
SMTP, não gera cobranças reais e não substitui homologação da integração v1.

## Pendências e limites

1. **Frontend / XSS:** há uso de `innerHTML`, código inline e JWT em `localStorage`.
   Validação de mídia não corrige todos os contextos HTML/JavaScript. Revisar a
   codificação de saída e remover `unsafe-inline` com nonces/hashes ou scripts externos.
   Conteúdo antigo armazenado não foi saneado por esta mudança.
2. **Abuso de tráfego:** bloqueio por conta não é rate limit global. Configurar limites
   por origem e tamanho de corpo no proxy, em especial cadastro, login, 2FA e imagens.
   O limite de mídia ocorre após o JSON ser recebido; webhook limita leitura a 64 KiB.
3. **Pagamentos:** conciliar estornos, falhas e cobranças legadas. Timeout entre a criação
   remota e a gravação local ainda exige conciliação; não há outbox nem garantia de
   exatamente uma criação remota. Pagamento recebido para agenda cancelada é registrado
   como pago, sem reabertura, e requer tratamento financeiro.
4. **Banco e agenda:** testar concorrência, fechamento de dia e reagendamentos com MySQL.
   Revisar consistência de fuso e regras completas de funcionamento. A migração fornecida
   pressupõe banco já compatível com as entidades anteriores, não com o dump antigo.
5. **Histórico:** retirar a senha de exemplo do arquivo atual não a apaga do Git. Se foi
   utilizada, trocá-la no ambiente correspondente. Nenhuma credencial real foi presumida.
6. **Sessões:** não há revogação individual de JWT; a validade permanece em 24 horas.
7. **Dependências:** atualização de versão não prova ausência de CVEs. Manter análise
   contínua de dependências e avaliar aplicabilidade dos alertas ao ambiente real.

## Referências

- [Webhooks v1 — payload e eventos](https://docs.abacatepay.com/pages/v1/webhooks)
- [Webhooks — secret e HMAC](https://docs.abacatepay.com/pages/webhooks)
- [Versões publicadas do Spring Boot no Maven Central](https://repo.maven.apache.org/maven2/org/springframework/boot/spring-boot-starter-parent/maven-metadata.xml)

Não é uma certificação ou uma declaração de ausência de vulnerabilidades.
