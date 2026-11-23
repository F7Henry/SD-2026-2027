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

## Testes (sprint 2)

Executa os testes automatizados a partir desta pasta:

```powershell
.\gradlew.bat test
```

Os testes verificam a cifra e a deteção de alterações, a serialização e o
padding dos chunks, a persistência e recuperação dos LSNs, a codificação
Base64 e a gravação/abertura dos ficheiros selados.

## Formato binário dos chunks

O conteúdo de um chunk antes da cifra é um bloco binário com magic `TPF1`.
Todos os números inteiros são escritos em **big-endian**.

| Campo | Tipo/tamanho |
|---|---|
| Magic | 4 bytes: `TPF1` |
| Número de registos | inteiro de 32 bits |
| LSN | inteiro de 64 bits |
| Instante: segundos UTC | inteiro de 64 bits |
| Instante: nanossegundos | inteiro de 32 bits |
| Autor: tamanho UTF-8 | inteiro de 32 bits |
| Autor: conteúdo | tamanho indicado, codificado em UTF-8 |
| Canal: tamanho UTF-8 | inteiro de 32 bits |
| Canal: conteúdo | tamanho indicado, codificado em UTF-8 |
| Texto: tamanho UTF-8 | inteiro de 32 bits |
| Texto: conteúdo | tamanho indicado, codificado em UTF-8 |

Os quatro campos seguintes aos tamanhos (`LSN`, instante, autor, canal e texto)
repetem-se uma vez por registo. Os campos de texto são escritos como sequência
de tamanho e bytes UTF-8, e não como texto em claro numa linha.

Depois da sequência de registos, o bloco é completado com bytes `0x00` até
atingir um tamanho múltiplo de `PAD_BYTES`. O valor predefinido é 4096 bytes
e pode ser alterado com `-Dtpf.padBytes`.

## Formato dos ficheiros selados

Cada chunk cifrado é gravado em:

- `selados/<chunkId>.bin`: ciphertext, incluindo a tag GCM de 128 bits.
- `selados/<chunkId>.hdr`: metadados em texto:

```text
<keyVersion>;<saltBase64>;<nonceBase64>
```

Por exemplo:

```text
1;c2FsdGUuLi4=;bm9uY2UuLi4=
```

O `salt` ocupa 32 bytes, o `nonce` ocupa 12 bytes e ambos são codificados com
Base64 no `.hdr`. O `chunkId` é o SHA-256 hexadecimal do conteúdo completo do
ficheiro `.bin`, incluindo a tag GCM.

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
