const { Event } = require("klasa");
const { MessageEmbed } = require("discord.js");
const moment = require("moment");
const sql = require("sqlite");
const ids = require("./../ids.json");
sql.open("./dane.sqlite");

module.exports = class extends Event {

  constructor(...args) {
    super(...args, {
      enabled: true
    });
    this.delayer = time => new Promise(res => setTimeout(() => res(), time));
  }

  async run() {
    this.client.ready = false;
    await this.delayer(3e3);
    await this.client.user.setPresence({status: "online", activity: { name: "FratikDev", type: "WATCHING" }});
    await this.client.channels.get(ids.c1).messages.fetch({limit: 100});
    await this.delayer(3e3);
    await this.delayer(3e3);
    await this.client.channels.get(ids.c2).messages.fetch(ids.m1);
    await this.delayer(3e3);
    await this.client.channels.get(ids.c2).messages.get(ids.m1).reactions.removeAll();
    await this.delayer(3e3);
    await this.client.channels.get(ids.c2).messages.get(ids.m1).react(ids.e1);
    await this.delayer(3e3);
    await this.client.channels.get(ids.c2).messages.get(ids.m1).react(ids.e2);
    this.client.setInterval(async() => {
      const rows = await sql.all("SELECT * FROM urlopy").catch(() => { return []; });
      for (const row of rows) {
        const dateOd = moment.unix(Number(row.od));
        const dateDo = moment.unix(Number(row.do));
        const member = this.client.guilds.get(ids.g1).member(row.userId);
        if (!member) continue;
        if (dateOd.unix() === moment(new Date().setHours(0, 0, 0, 0)).unix()) {
          if (dateOd.diff(dateDo) <= -1209600000) {
            await member.roles.remove(ids.r1);
          }
        }
        if (dateDo.unix() === moment(new Date().setHours(0, 0, 0, 0)).unix()) {
          if (!member.roles.has(ids.r1)) {
            await member.roles.add(ids.r1);
          }
          this.client.events.get("messageDelete").ignored.add(row.msg);
          await this.client.channels.get(ids.c1).messages.get(row.msg).delete();
          const logEmbed = new MessageEmbed()
            .setAuthor(member.user.tag, member.user.displayAvatarURL().replace(".webp", ".png"))
            .setColor("#ff8c00")
            .setFooter("Koniec urlopu")
            .addField("Powód końca", "Czas się skończył!");
          await this.client.channels.get(ids.c3).send(logEmbed);
          await sql.run(`DELETE FROM urlopy WHERE userId = ${member.id}`).catch(() => undefined);
          await sql.run("INSERT INTO cooldowny (userId, until) VALUES (?, ?)", [member.id, moment(new Date().setHours(0, 0, 0, 0)).add(2, "weeks").unix()])
            .catch(async() => {
              await sql.run("CREATE TABLE IF NOT EXISTS cooldowny (userId TEXT, until INTEGER)");
              await sql.run("INSERT INTO cooldowny (userId, until) VALUES (?, ?)", [member.id, moment(new Date().setHours(0, 0, 0, 0)).add(2, "weeks").unix()]);
            });
        }
      }
    }, 15e3);
    this.client.setInterval(async() => {
      const rows = await sql.all("SELECT * FROM zweryfikowani").catch(() => { return []; });
      const roleDoOdebrania = [];
      for (const row of rows) {
        if (!row.ostatniaWiadomosc) continue;
        const dateOd = moment.unix(Number(row.ostatniaWiadomosc));
        const dateDo = moment(new Date().setHours(0, 0, 0, 0));
        const member = this.client.guilds.get(ids.g1).member(row.userId);
        if (!member) continue;
        if (moment(dateOd.toDate().setHours(0, 0, 0, 0)).add(4, "days").unix() <= dateDo.unix()) {
          if (member.roles.has(ids.r3)) {
            await sql.run(`DELETE FROM zweryfikowani WHERE userId = ${member.id}`);
            roleDoOdebrania.push(member.user);
            await member.roles.remove(ids.r3);
          }
        }
      }
      if (roleDoOdebrania.length) {
        this.client.console.log(`Zabrano role ${roleDoOdebrania.length} osobom.`);
        this.client.console.log("Te osoby to:");
        this.client.console.log(roleDoOdebrania.map(u => u.tag));
      }
    }, 30e3);
    this.client.ready = true;
  }

};
