const { Event } = require("klasa");
const { MessageEmbed } = require("discord.js");
const moment = require("moment");
const sql = require("sqlite");
sql.open("./dane.sqlite");

module.exports = class extends Event {

  constructor(...args) {
    super(...args, {
      enabled: true
    });
    this.ignored = new Set();
  }

  async run(msg) {
    if (msg.author.id === this.client.user.id) return;
    switch (msg.channel.id) {
    case "424310954507894784": {
      if (!msg.poprawnyFormat) return;
      if (this.ignored.has(msg.id)) return this.ignored.delete(msg.id);
      await sql.run(`DELETE FROM urlopy WHERE userId = ${msg.author.id}`).catch(() => undefined);
      await sql.run("INSERT INTO cooldowny (userId, until) VALUES (?, ?)", [msg.author.id, moment(new Date().setHours(0, 0, 0, 0)).add(2, "weeks").unix()])
        .catch(async() => {
          await sql.run("CREATE TABLE IF NOT EXISTS cooldowny (userId TEXT, until INTEGER)");
          await sql.run("INSERT INTO cooldowny (userId, until) VALUES (?, ?)", [msg.author.id, moment(new Date().setHours(0, 0, 0, 0)).add(2, "weeks").unix()]);
        });
      let failed = false;
      const _msg = await msg.channel.send(`${msg.author.toString()}: Powód anulowania urlopu?`);
      this.client.events.get("message").ignore = msg.author.id;
      const awaited = await msg.channel.awaitMessages((m) => m.author.id === msg.author.id, {max: 1, time: 300e3, errors: ["time"]})
        .catch(() => {
          failed = true;
          this.client.events.get("message").ignore = false;
          return _msg.delete();
        });
      this.ignored.add(awaited.first().id);
      if (awaited.first().content.length >= 1000) {
        failed = true;
        this.client.events.get("message").ignore = false;
        await _msg.delete();
        await awaited.first().delete();
        const __msg = msg.channel.send("Powód nie może być dłuższy od 1k znaków. Używam \"brak powodu\".");
        setTimeout(() => __msg.delete(), 5e3);
      }
      this.client.events.get("message").ignore = false;
      await _msg.delete();
      if (!failed) await awaited.first().delete();
      const logEmbed = new MessageEmbed()
        .setAuthor(msg.author.tag, msg.author.displayAvatarURL().replace(".webp", ".png"))
        .setColor("#ff8c00")
        .setFooter("Anulowany urlop | usunięcie wiadomości")
        .addField("Powód anulowania", failed ? "brak powodu" : awaited.first().content);
      return this.client.channels.get("462258545908514820").send(logEmbed);
    }
    }
  }

};