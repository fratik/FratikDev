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
    this.delayer = time => new Promise(res => setTimeout(() => res(), time));
  }

  async run() {
    this.client.ready = false;
    await this.delayer(3e3);
    await this.client.user.setPresence({status: "online", activity: { name: "FratikDev", type: "WATCHING" }});
    await this.client.channels.get("424310954507894784").messages.fetch({limit: 100});
    await this.delayer(3e3);
    await this.delayer(3e3);
    await this.client.channels.get("471367851316609034").messages.fetch("471368232499150858");
    await this.delayer(3e3);
    await this.client.channels.get("471367851316609034").messages.get("471368232499150858").reactions.removeAll();
    await this.delayer(3e3);
    await this.client.channels.get("471367851316609034").messages.get("471368232499150858").react("436919889207361536");
    await this.delayer(3e3);
    await this.client.channels.get("471367851316609034").messages.get("471368232499150858").react("436919889232658442");
    this.client.setInterval(async() => {
      const rows = await sql.all("SELECT * FROM urlopy").catch(() => { return []; });
      for (const row of rows) {
        const dateOd = moment.unix(Number(row.od));
        const dateDo = moment.unix(Number(row.do));
        const member = this.client.guilds.get("345655892882096139").member(row.userId);
        if (!member) continue;
        if (dateOd.unix() === moment(new Date().setHours(0, 0, 0, 0)).unix()) {
          if (dateOd.diff(dateDo) <= -1209600000) {
            await member.roles.remove("371306270030037024");
          }
        }
        if (dateDo.unix() === moment(new Date().setHours(0, 0, 0, 0)).unix()) {
          if (!member.roles.has("371306270030037024")) {
            await member.roles.add("371306270030037024");
          }
          this.client.events.get("messageDelete").ignored.add(row.msg);
          await this.client.channels.get("424310954507894784").messages.get(row.msg).delete();
          const logEmbed = new MessageEmbed()
            .setAuthor(member.user.tag, member.user.displayAvatarURL().replace(".webp", ".png"))
            .setColor("#ff8c00")
            .setFooter("Koniec urlopu")
            .addField("Powód końca", "Czas się skończył!");
          await this.client.channels.get("462258545908514820").send(logEmbed);
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
      for (const row of rows) {
        const dateOd = moment.unix(Number(row.od));
        const dateDo = moment.unix(Number(row.do));
        const member = this.client.guilds.get("345655892882096139").member(row.userId);
        if (!member) continue;
        if (dateOd.unix() === moment(new Date().setHours(0, 0, 0, 0)).unix()) {
          if (dateOd.diff(dateDo) <= -1209600000) {
            await member.roles.remove("371306270030037024");
          }
        }
        if (dateDo.unix() === moment(new Date().setHours(0, 0, 0, 0)).unix()) {
          if (!member.roles.has("371306270030037024")) {
            await member.roles.add("371306270030037024");
          }
          this.client.events.get("messageDelete").ignored.add(row.msg);
          await this.client.channels.get("424310954507894784").messages.get(row.msg).delete();
          const logEmbed = new MessageEmbed()
            .setAuthor(member.user.tag, member.user.displayAvatarURL().replace(".webp", ".png"))
            .setColor("#ff8c00")
            .setFooter("Koniec urlopu")
            .addField("Powód końca", "Czas się skończył!");
          await this.client.channels.get("462258545908514820").send(logEmbed);
          await sql.run(`DELETE FROM urlopy WHERE userId = ${member.id}`).catch(() => undefined);
          await sql.run("INSERT INTO cooldowny (userId, until) VALUES (?, ?)", [member.id, moment(new Date().setHours(0, 0, 0, 0)).add(2, "weeks").unix()])
            .catch(async() => {
              await sql.run("CREATE TABLE IF NOT EXISTS cooldowny (userId TEXT, until INTEGER)");
              await sql.run("INSERT INTO cooldowny (userId, until) VALUES (?, ?)", [member.id, moment(new Date().setHours(0, 0, 0, 0)).add(2, "weeks").unix()]);
            });
        }
      }
    }, 30e3);
    this.client.ready = true;
  }

};
