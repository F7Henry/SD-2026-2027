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

import java.util.Objects;

public final class LeaderBot extends ListenerAdapter {
    // Nomes usados para identificar o canal de arquivo e o papel de administração.
    private static final String ARCHIVE_CHANNEL = "arquivo";
    private static final String ADMIN_ROLE = "admin-arquivo";

    // Identificador do servidor onde o bot deve trabalhar. Fica guardado na
    // instância para que os handlers de eventos possam confirmar a origem de
    // cada evento e ignorar os que vêm de outros servidores.
    private final String guildId;

    public LeaderBot(String guildId) {
        this.guildId = guildId;
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
            case "registar" -> event.reply("`/registar` recebido: texto="
                    + quote(event.getOption("texto").getAsString())
                    + " — ainda não implementado").queue();
            case "estado" -> event.reply("`/estado` recebido — ainda não implementado.").queue();
            case "config" -> handleConfig(event);
            case "recuperar" -> handleRecovery(event);
            case "replica-token" -> event.reply("`/replica-token` recebido — ainda não implementado.").queue();
            case "replica-remover" -> event.reply("`/replica-remover` recebido: id="
                    + event.getOption("id").getAsString() + " — ainda não implementado").queue();
            case "rajada" -> event.reply("`/rajada` recebido: n="
                    + event.getOption("n").getAsLong() + ", intervaloMs="
                    + event.getOption("intervaloms").getAsLong()
                    + " — ainda não implementado").queue();
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
            channel.sendMessage("Mensagem em `#arquivo` detetada — ainda não implementado.").queue();
        }
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
