package pl.fratik.FratikDev;

import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.TextChannel;

@SuppressWarnings("CanBeFinal")
public class Config {
    public static Config instance;

    public int blokadaOd = 23;
    public int blokadaDo = 7;
    public String guildId = "";
    public Kanaly kanaly = new Kanaly();
    public Wiadomosci wiadomosci = new Wiadomosci();
    public Emotki emotki = new Emotki();
    public Role role = new Role();
    public Database database = new Database();
    public String api = "";
    public String apiKey = "";
    public Funkcje funkcje = new Funkcje();

    public static class Kanaly {
        public String urlopyGa = "";
        public String zatwierdzRegulamin = "";
        public String logiUrlopow = "";
        public String glownyKanal = "";
        public String logiWeryfikacji = "";
    }

    public static class Wiadomosci {
        public String zatwierdzRegulaminTresc = "";
        public String zatwierdzRegulaminWiadomoscBota = null;
        public String urlopTresc = "";
        public String urlopWiadomoscBota = null;
    }

    public static class Emotki {
        public String greenTick = "436919889207361536";
        public String redTick = "436919889232658442";
        public String loading = "503651397049516053";
    }

    public static class Role {
        public String globalAdmin = "";
        public String admin = "";
        public String zarzadzajacyGa = "";
        public String rolaUzytkownika = "";
    }

    public static class Database {
        public String jdbcUrl = "jdbc:postgresql://localhost/fratikdev";
        public String user = "postgres";
        public String password = "";
    }

    public static class Funkcje {
        public Komendy komendy = new Komendy();
        public boolean urlopy = true;
        public Weryfikacja weryfikacja = new Weryfikacja();
        public CustomRole customRole = new CustomRole();
    }

    public static class Komendy {
        public boolean wlaczone = true;
        public boolean suffix = true;
        public boolean naprawnicki = true;
        public boolean weryfikacja = true;
        public boolean ustawNick = true; //administracyjna opcja, zwykla zmiana jest niżej — Weryfikacja.zezwolNaZmianeNicku
        public boolean blacklistNick = true;
        public boolean edytujRole = true; //administracyjna opcja, patrz CustomRole.wlaczone
        public boolean usunRole = true;
        public boolean blacklistRole = true;
    }

    public static class CustomRole {
        public boolean wlaczone = true;
        public boolean tylkoDlaBoosterow = true;
        public boolean zezwolNaZmianeKoloru = true;
        public boolean zezwolNaZmianeIkony = true;
        public String weryfikacjaAdministracyjna = null; // id kanału
        public boolean logi = true;

        public TextChannel getWeryfikacjaAdministracyjna(JDA jda) {
            return weryfikacjaAdministracyjna == null ? null : jda.getTextChannelById(weryfikacjaAdministracyjna);
        }
    }

    public static class Weryfikacja {
        public boolean wlaczone = true;
        public boolean zabierzRole = true;
        public boolean zabierajBoosterom = false;
        public boolean restrykcje = true;
        public boolean naprawNick = true;
        public String domyslnyNick = "mam rakowy nick";
        public boolean logi = true;
        public boolean zabierzRolePrzyZmianieAvataru = true;
        public boolean zezwolNaZmianeNicku = true;
    }
}
