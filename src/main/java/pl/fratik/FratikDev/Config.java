package pl.fratik.FratikDev;

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

    public class Kanaly {
        public String urlopyGa = "";
        public String zatwierdzRegulamin = "";
        public String logiUrlopow = "";
        public String glownyKanal = "";
        public String logiWeryfikacji = "";
    }

    public class Wiadomosci {
        public String zatwierdzRegulaminWiadomosc = "";
    }

    public class Emotki {
        public String greenTick = "436919889207361536";
        public String redTick = "436919889232658442";
        public String loading = "503651397049516053";
    }

    public class Role {
        public String globalAdmin = "";
        public String admin = "";
        public String zarzadzajacyGa = "";
        public String rolaUzytkownika = "";
    }

    public class Database {
        public String jdbcUrl = "jdbc:postgresql://localhost/fratikdev";
        public String user = "postgres";
        public String password = "";
    }
}
