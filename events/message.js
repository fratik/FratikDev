const { Event } = require("klasa");
const { MessageEmbed } = require("discord.js");
const moment = require("moment");
moment.locale("pl");
const sql = require("sqlite");
sql.open("./dane.sqlite");

const REGEXOD = /(?:od:? ?)(0?[1-9]|[12]\d|3[01])(?:\.|\/|-)(0?[1-9]|1[0-2])(?:\.|\/|-)(19|20\d{2})/img;
const REGEXDO = /(?:do:? ?)(0?[1-9]|[12]\d|3[01])(?:\.|\/|-)(0?[1-9]|1[0-2])(?:\.|\/|-)(19|20\d{2})/img;

module.exports = class extends Event {

  constructor(...args) {
    super(...args, {
      enabled: true
    });
    this.ignore = false;
    this.delayer = time => new Promise(res => setTimeout(() => res(), time));
  }

  async run(msg) {
    if (msg.author.id === this.client.user.id) return;
    if (this.ignore === msg.author.id) return;
    switch (msg.channel.id) {
    case "424310954507894784": {
      REGEXOD.lastIndex = null;
      REGEXDO.lastIndex = null;
      const regDataOd = REGEXOD.exec(msg.content); //[cały string, dzień, miesiąc, rok]
      const regDataDo = REGEXDO.exec(msg.content); //[cały string, dzień, miesiąc, rok]
      if (!regDataOd || !regDataDo) {
        this.client.events.get("messageDelete").ignored.add(msg.id);
        await msg.delete();
        const _msg = await msg.channel.send(`${msg.author.toString()}: Nieprawidłowy format!`);
        const logEmbed = new MessageEmbed()
          .setAuthor(msg.author.tag, msg.author.displayAvatarURL().replace(".webp", ".png"))
          .setColor("#ff0000")
          .setFooter("Odrzucony urlop")
          .addField("Powód odrzucenia", "Nieprawidłowy format")
          .addField("Osoba odrzucająca", this.client.user.tag);
        await this.client.channels.get("462258545908514820").send(logEmbed);
        setTimeout(() => _msg.delete().catch(() => undefined), 5e3);
        return;
      }
      const dataOd = moment(new Date(`${regDataOd[3]}/${regDataOd[2]}/${regDataOd[1]}`).setHours(0, 0, 0, 0)); //przyjmuje w formacie "rok/miesiąc/dzień"
      const dataDo = moment(new Date(`${regDataDo[3]}/${regDataDo[2]}/${regDataDo[1]}`).setHours(0, 0, 0, 0)); //przyjmuje w formacie "rok/miesiąc/dzień"
      if (dataOd.diff(dataDo) >= -259200000) {
        this.client.events.get("messageDelete").ignored.add(msg.id);
        await msg.delete();
        const _msg = await msg.channel.send(`${msg.author.toString()}: Urlop musi trwać dłużej niż 3 dni by być zarejestrowany!`);
        const logEmbed = new MessageEmbed()
          .setAuthor(msg.author.tag, msg.author.displayAvatarURL().replace(".webp", ".png"))
          .setColor("#ff0000")
          .setFooter("Odrzucony urlop")
          .addField("Powód odrzucenia", "Różnica dat krótsza od 3 dni")
          .addField("Osoba odrzucająca", this.client.user.tag);
        await this.client.channels.get("462258545908514820").send(logEmbed);
        setTimeout(() => _msg.delete(), 5e3);
        return;
      }
      const row = await sql.get(`SELECT * FROM urlopy WHERE userId = ${msg.author.id}`)
        .catch(async(err) => {
          this.client.console.error(err);
          await sql.run("CREATE TABLE IF NOT EXISTS urlopy (userId TEXT, od INTEGER, do INTEGER, zatwierdzony TEXT, msg TEXT)");
          return undefined;
        });
      const cooldown = await sql.get(`SELECT * FROM cooldowny WHERE userId = ${msg.author.id}`).catch(() => undefined);
      if (cooldown) {
        if (Number(cooldown.until) >= moment(new Date().setHours(0, 0, 0, 0)).unix()) {
          this.client.events.get("messageDelete").ignored.add(msg.id);
          await msg.delete();
          const _msg = await msg.channel.send(`${msg.author.toString()}: Twój urlop jest na cooldownie przez ${moment.unix(cooldown.until).fromNow(true)}, bo muszą być 2 tygodnie przerwy między urlopami.`);
          const logEmbed = new MessageEmbed()
            .setAuthor(msg.author.tag, msg.author.displayAvatarURL().replace(".webp", ".png"))
            .setColor("#ff0000")
            .setFooter("Odrzucony urlop")
            .addField("Powód odrzucenia", "Cooldown")
            .addField("Osoba odrzucająca", this.client.user.tag);
          await this.client.channels.get("462258545908514820").send(logEmbed);
          setTimeout(() => _msg.delete(), 5e3);
          return;
        }
      }
      if (row) {
        this.client.events.get("messageDelete").ignored.add(msg.id);
        await msg.delete();
        const _msg = await msg.channel.send(`${msg.author.toString()}: Już masz zarejestrowany urlop!`);
        setTimeout(() => _msg.delete(), 5e3);
        return;
      }
      await sql.run("INSERT INTO urlopy (userId, od, do, zatwierdzony, msg) VALUES (?, ?, ?, ?, ?)", [msg.author.id, dataOd.unix(), dataDo.unix(), false, msg.id])
        .catch(async(err) => {
          this.client.console.error(err);
          await sql.run("CREATE TABLE IF NOT EXISTS urlopy (userId TEXT, od INTEGER, do INTEGER, zatwierdzony TEXT, msg TEXT)");
          return sql.run("INSERT INTO urlopy (userId, od, do, zatwierdzony, msg) VALUES (?, ?, ?, ?, ?)", [msg.author.id, dataOd.unix(), dataDo.unix(), false, msg.id]);
        });
      await this.delayer(6e2);
      await msg.react("436919889207361536");
      await this.delayer(6e2);
      await msg.react("436919889232658442");
      await this.delayer(6e2);
      await msg.channel.send("<@&414418843352432640>");
      return;
    }
    default: {
      const row = await sql.get(`SELECT * FROM zweryfikowani WHERE userId = ${msg.author.id}`);
      if (!row) await sql.run("INSERT INTO zweryfikowani (userId, date, ostatniaWiadomosc) VALUES (?, ?, ?)", [msg.author.id, moment().unix(), msg.createdTimestamp]);
      else {
        await sql.run(`UPDATE zweryfikowani SET ostatniaWiadomosc = ${msg.createdTimestamp} WHERE userId = ${msg.author.id}`)
          .catch(async() => {
            await sql.run("CREATE TABLE IF NOT EXISTS zweryfikowani (userId TEXT, date INTEGER, ostatniaWiadomosc INTEGER)");
            await sql.run("INSERT INTO zweryfikowani (userId, date, ostatniaWiadomosc) VALUES (?, ?, ?)", [msg.author.id, moment().unix(), msg.createdTimestamp]);
          });
      }
    }
    }
  }

};
