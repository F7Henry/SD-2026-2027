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

## Arranque no Windows CMD

No CMD, primeiro compila e testa o projeto com:

```cmd
gradlew.bat build
```

Depois define as variáveis de ambiente e inicia o bot com:

```cmd
set DISCORD_TOKEN=token-local-do-bot
set DISCORD_GUILD_ID=id-do-servidor-de-teste
gradlew.bat run
```

Para remover as variáveis na sessão atual:

```cmd
set DISCORD_TOKEN=
set DISCORD_GUILD_ID=
```

## Testes

Executa os testes automatizados a partir desta pasta:

```powershell
.\gradlew.bat test
```

Os testes verificam a cifra e a deteção de alterações, a serialização e o
padding dos chunks, a persistência e recuperação dos LSNs, a codificação
Base64 e a gravação/abertura dos ficheiros selados.

## Utilização no Discord

Os comandos são registados no servidor configurado e aparecem no autocomplete
após o arranque:

- `/registar texto:<mensagem>` persiste uma mensagem e responde com o LSN.
- Mensagens escritas em `#arquivo` são persistidas automaticamente.
- `/rajada n:<quantidade> intervaloms:<milissegundos>` injeta registos sintéticos
  e requer o papel `admin-arquivo`.

Os dados locais são guardados em `registos.log`, `eventos-lider.txt`,
`master.key` e na pasta `selados`. Réplicas e recuperação distribuída ainda
não fazem parte deste sprint.
