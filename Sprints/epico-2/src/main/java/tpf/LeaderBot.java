package tpf;

import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import org.jetbrains.annotations.NotNull;

import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public final class LeaderBot extends ListenerAdapter {
    // Nomes usados para identificar o canal de arquivo e o papel de administração.
    private static final String ARCHIVE_CHANNEL = "arquivo";
    private static final String ADMIN_ROLE = "admin-arquivo";

    // Identificador do servidor onde o bot deve trabalhar. Fica guardado na
    // instância para que os handlers de eventos possam confirmar a origem de
    // cada evento e ignorar os que vêm de outros servidores.
    private final String guildId;
    private final RegistoStore registoStore;
    private final byte[] masterKey;
    private final Path sealedDirectory;
    private final ScheduledExecutorService scheduler;
    private Chunk chunk = new Chunk();

    public LeaderBot(String guildId) {
        this.guildId = guildId;
        this.registoStore = new RegistoStore();
        this.masterKey = Wire.loadOrCreateMasterKey();
        this.sealedDirectory = Path.of("selados");
        this.scheduler = Executors.newSingleThreadScheduledExecutor();
        scheduler.scheduleAtFixedRate(this::sealIfExpired, Wire.SELAGEM_MS,
                Wire.SELAGEM_MS, TimeUnit.MILLISECONDS);
    }

    public static void main(String[] args) throws InterruptedException {
        // O token e o identificador do servidor são lidos do ambiente para não
        // ficarem guardados no código-fonte nem serem enviados para o repositório.
        String token = requiredEnvironment("DISCORD_TOKEN");
        String guildId = requiredEnvironment("DISCORD_GUILD_ID");

        // Cria a ligação do bot ao Discord, regista este listener e espera até
        // que a ligação esteja pronta para receber eventos.
        JDA jda = JDABuilder.createDefault(token)
                .addEventListeners(new LeaderBot(guildId))
                .build()
                .awaitReady();

        // Garante que o identificador configurado corresponde a um servidor
        // que o bot consegue aceder.
        Objects.requireNonNull(jda.getGuildById(guildId),
                "DISCORD_GUILD_ID não corresponde a um servidor acessível pelo bot");

        // Os comandos são registados apenas no servidor de teste. Desta forma,
        // ficam disponíveis imediatamente no autocomplete desse servidor.
        jda.getGuildById(guildId).updateCommands().addCommands(
                Commands.slash("registar", "Regista um texto no arquivo")
                        .addOption(OptionType.STRING, "texto", "Texto a registar", true),
                Commands.slash("estado", "Mostra o estado provisório do líder"),
                Commands.slash("config", "Configura o fator de replicação")
                        .addOptions(new OptionData(OptionType.STRING, "operacao", "Operação",
                                        true).addChoice("r", "r"),
                                new OptionData(OptionType.INTEGER, "n", "Novo fator R", true)
                                        .setRequiredRange(1, 100)),
                Commands.slash("recuperar", "Recupera um intervalo de registos")
                        .addOptions(new OptionData(OptionType.INTEGER, "de", "LSN inicial", true)
                                .setRequiredRange(0, Integer.MAX_VALUE),
                                new OptionData(OptionType.INTEGER, "ate", "LSN final", true)
                                .setRequiredRange(0, Integer.MAX_VALUE)),
                Commands.slash("replica-token", "Gera um token provisório de réplica"),
                Commands.slash("replica-remover", "Remove uma réplica")
                        .addOption(OptionType.STRING, "id", "Identificador da réplica", true),
                Commands.slash("rajada", "Configura uma rajada de registos")
                        .addOptions(new OptionData(OptionType.INTEGER, "n", "Número de registos", true)
                                        .setRequiredRange(1, 100000),
                                new OptionData(OptionType.INTEGER, "intervaloms", "Intervalo em ms", true)
                                        .setRequiredRange(0, Integer.MAX_VALUE))
        ).queue();

        // Remove os comandos de qualquer outro servidor onde o bot ainda
        // esteja presente (por exemplo, o servidor antigo), para que não
        // fiquem visíveis lá. Os eventos desses servidores são de qualquer
        // forma ignorados pelos handlers.
        jda.getGuilds().stream()
                .filter(guild -> !guildId.equals(guild.getId()))
                .forEach(guild -> guild.updateCommands().queue());
    }

    @Override
    public void onSlashCommandInteraction(@NotNull SlashCommandInteractionEvent event) {
        // Só os comandos do servidor configurado são tratados; interações
        // vindas de outros servidores (incluindo o antigo) são ignoradas.
        if (event.getGuild() == null || !guildId.equals(event.getGuild().getId())) {
            return;
        }

        // O nome permite encaminhar cada interação para o tratamento adequado.
        String name = event.getName();

        // Os comandos de administração só podem ser usados por membros com
        // o papel definido em ADMIN_ROLE.
        if (requiresAdmin(name) && !hasAdminRole(event.getMember())) {
            event.reply("Permissão recusada: é necessário o papel `admin-arquivo`.").setEphemeral(true).queue();
            return;
        }

        // Responde provisoriamente a cada comando e ecoa os argumentos recebidos.
        switch (name) {
            case "registar" -> {
                try {
                    Registo registo = registar(event.getUser().getName(), "comando",
                            event.getOption("texto").getAsString());
                    event.reply("Registo persistido com LSN " + registo.lsn()).queue();
                } catch (RuntimeException exception) {
                    event.reply("Não foi possível persistir o registo: "
                            + exception.getMessage()).setEphemeral(true).queue();
                }
            }
            case "estado" -> event.reply("`/estado` recebido — ainda não implementado.").queue();
            case "config" -> handleConfig(event);
            case "recuperar" -> handleRecovery(event);
            case "replica-token" -> event.reply("`/replica-token` recebido — ainda não implementado.").queue();
            case "replica-remover" -> event.reply("`/replica-remover` recebido: id="
                    + event.getOption("id").getAsString() + " — ainda não implementado").queue();
            case "rajada" -> handleBurst(event);
            default -> event.reply("Comando desconhecido.").setEphemeral(true).queue();
        }
    }

    @Override
    public void onMessageReceived(@NotNull MessageReceivedEvent event) {
        // Ignora mensagens enviadas por bots para evitar respostas automáticas
        // repetidas ou ciclos entre bots.
        if (event.getAuthor().isBot()) {
            return;
        }

        // Apenas mensagens do servidor configurado interessam; as mensagens
        // de outros servidores onde o bot esteja presente são ignoradas.
        if (event.getGuild() == null || !guildId.equals(event.getGuild().getId())) {
            return;
        }

        // Apenas mensagens de um canal de texto chamado "arquivo" pertencem
        // ao fluxo; os outros canais são ignorados.
        if (event.getChannel() instanceof TextChannel channel
                && ARCHIVE_CHANNEL.equals(channel.getName())) {
            try {
                Registo registo = registar(event.getAuthor().getName(), channel.getName(),
                        event.getMessage().getContentRaw());
                channel.sendMessage("Registo persistido com LSN " + registo.lsn()).queue();
            } catch (RuntimeException exception) {
                channel.sendMessage("Não foi possível persistir o registo.").queue();
            }
        }
    }

    private synchronized Registo registar(String autor, String canal, String texto) {
        Registo registo = registoStore.registar(autor, canal, texto);
        boolean excedeuLimite = chunk.adicionar(registo);
        if (excedeuLimite) {
            selarChunk();
        }
        return registo;
    }

    private synchronized void sealIfExpired() {
        if (!chunk.vazio() && chunk.deveSelarPorTempo(Instant.now())) {
            selarChunk();
        }
    }

    private void selarChunk() {
        Chunk current = chunk;
        byte[] padded = current.serializar();
        ChunkCrypto.SealedData sealed = Sealed.write(sealedDirectory, masterKey, padded);
        List<Registo> records = current.registos();
        registoStore.evento("SELADO", "chunkId=" + sealed.chunkId()
                + ",lsnDe=" + records.get(0).lsn()
                + ",lsnAte=" + records.get(records.size() - 1).lsn()
                + ",tamanho=" + sealed.ciphertext().length);
        chunk = new Chunk();
    }

    private void handleBurst(SlashCommandInteractionEvent event) {
        long count = event.getOption("n").getAsLong();
        long interval = event.getOption("intervaloms").getAsLong();
        event.deferReply().queue(hook -> scheduler.execute(() -> {
            try {
                for (long i = 0; i < count; i++) {
                    registar("rajada", "comando",
                            "registo-sintetico-" + (i + 1));
                    if (interval > 0 && i + 1 < count) {
                        Thread.sleep(interval);
                    }
                }
                hook.editOriginal("Rajada concluída: " + count + " registos.").queue();
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                hook.editOriginal("Rajada interrompida.").queue();
            } catch (RuntimeException exception) {
                hook.editOriginal("Rajada falhou: " + exception.getMessage()).queue();
            }
        }));
    }

    private static void handleConfig(SlashCommandInteractionEvent event) {
        // Obtém o novo fator R e rejeita valores menores que um.
        long n = event.getOption("n").getAsLong();
        event.reply(n < 1
                ? "Argumento inválido: `n` tem de ser maior que zero."
                : "`/config r` recebido: n=" + n + " — ainda não implementado").queue();
    }

    private static void handleRecovery(SlashCommandInteractionEvent event) {
        // Lê os limites do intervalo e garante que o início não ultrapassa o fim.
        long from = event.getOption("de").getAsLong();
        long to = event.getOption("ate").getAsLong();
        if (from > to) {
            event.reply("Argumentos inválidos: `de` não pode ser maior que `ate`.").setEphemeral(true).queue();
            return;
        }
        event.reply("`/recuperar` recebido: de=" + from + ", ate=" + to
                + " — ainda não implementado").queue();
    }

    private static boolean requiresAdmin(String command) {
        // Lista dos comandos que alteram configurações ou controlam réplicas.
        return command.equals("config") || command.equals("replica-token")
                || command.equals("replica-remover") || command.equals("rajada");
    }

    private static boolean hasAdminRole(Member member) {
        // Verifica se o membro existe e se algum dos seus papéis tem o nome
        // reservado aos administradores do arquivo.
        return member != null && member.getRoles().stream()
                .map(Role::getName)
                .anyMatch(ADMIN_ROLE::equals);
    }

    private static String requiredEnvironment(String name) {
        // Variáveis ausentes ou vazias indicam uma configuração incompleta.
        String value = System.getenv(name);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("Defina a variável de ambiente " + name);
        }
        return value;
    }

    private static String quote(String value) {
        // Delimita o texto na resposta e escapa crases para não quebrar a
        // formatação Markdown usada pelo Discord.
        return "`" + value.replace("`", "\\`") + "`";
    }
}
