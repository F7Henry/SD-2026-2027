# tpf-lider

Base do líder Discord do Sprint E2-01. Este sprint apenas recebe os comandos,
valida argumentos, aplica a autorização provisória e deteta mensagens em
`#arquivo`; o arquivo distribuído será implementado nos sprints seguintes.

## Pré-requisitos

- Java 17+
- Gradle 8+ (ou o Gradle Wrapper incluído)
- Uma aplicação criada no [Discord Developer Portal](https://discord.com/developers/applications)
- O intent **Message Content** ativado em **Bot > Privileged Gateway Intents**

No portal, cria o bot, copia o token em **Reset Token** e guarda-o localmente.
Nunca o coloques no código ou num commit.

## Configuração do servidor

Cria um servidor de teste com o canal `arquivo` e o papel `admin-arquivo`.
Em **OAuth2 > URL Generator**, seleciona os scopes `bot` e
`applications.commands`, com permissões para ver canais e enviar mensagens.
Abre o URL gerado para adicionar o bot ao servidor.

## Arranque no Windows PowerShell

Obtém o ID do servidor ativando o modo de programador do Discord, clicando com
o botão direito no servidor e escolhendo **Copy Server ID**.

```powershell
$env:DISCORD_TOKEN = "token-local-do-bot"
$env:DISCORD_GUILD_ID = "id-do-servidor-de-teste"
.\gradlew.bat run
```

O token existe apenas na sessão atual do PowerShell. Para o remover:

```powershell
Remove-Item Env:\DISCORD_TOKEN
Remove-Item Env:\DISCORD_GUILD_ID
```

Os comandos são registados como comandos do servidor e devem aparecer no
autocomplete após o arranque. Não existe ainda cifra, registo em ficheiro,
réplicas ou recuperação neste sprint.
