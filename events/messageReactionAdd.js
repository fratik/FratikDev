const { Event } = require("klasa");
const { MessageEmbed } = require("discord.js");
const moment = require("moment");
const sql = require("sqlite");
const ids = require("./../ids.json");
sql.open("./dane.sqlite");

const REGEXOD = /(?:od:? )(0?[1-9]|[12]\d|3[01])(?:\.|\/|-)(0?[1-9]|1[0-2])(?:\.|\/|-)(19|20\d{2})/img;
const REGEXDO = /(?:do:? )(0?[1-9]|[12]\d|3[01])(?:\.|\/|-)(0?[1-9]|1[0-2])(?:\.|\/|-)(19|20\d{2})/img;

module.exports = class extends Event {

  constructor(...args) {
    super(...args, {
      enabled: true
    });
    this.delayer = time => new Promise(res => setTimeout(() => res(), time));
  }

  async run(reaction, user) {
    if (user.id === this.client.user.id) return;
    switch (reaction.message.channel.id) {
    case ids.c1: {
      if (reaction.message.author.id === user.id) return reaction.users.remove(user.id);
      if (!reaction.message.guild.members.get(user.id).roles.has(ids.r2)) return reaction.users.remove(user.id);
      for (const w of reaction.message.channel.messages.filter(m => m.author.id === this.client.user.id && m.content === "<@&" + ids.r2 + ">").array()) await w.delete().catch(() => undefined);
      switch (reaction.emoji.name) {
      case "redTick": {
        let failed = false;
        const _msg = await reaction.message.channel.send(`${user.toString()}: Powód odrzucenia urlopu?`);
        this.client.events.get("message").ignore = user.id;
        const awaited = await reaction.message.channel.awaitMessages((m) => m.author.id === user.id, {max: 1, time: 300e3, errors: ["time"]})
          .catch(async() => {
            failed = true;
            this.client.events.get("message").ignore = false;
            await _msg.delete();
            await reaction.users.remove(user.id);
          });
        if (awaited.first().content.length >= 1000) {
          failed = true;
          this.client.events.get("message").ignore = false;
          await _msg.delete();
          await awaited.first().delete();
          await reaction.users.remove(user.id);
          const __msg = reaction.message.channel.send("Powód nie może być dłuższy od 1k znaków.");
          setTimeout(() => __msg.delete(), 5e3);
        }
        this.client.events.get("message").ignore = false;
        if (failed) return;
        await _msg.delete();
        await awaited.first().delete();
        this.client.events.get("messageDelete").ignored.add(reaction.message.id);
        await reaction.message.delete();
        const logEmbed = new MessageEmbed()
          .setAuthor(reaction.message.author.tag, reaction.message.author.displayAvatarURL().replace(".webp", ".png"))
          .setColor("#ff0000")
          .setFooter("Odrzucony urlop")
          .addField("Powód odrzucenia", awaited.first().content)
          .addField("Osoba odrzucająca", user.tag);
        await sql.run(`DELETE FROM urlopy WHERE userId = ${reaction.message.author.id}`);
        await reaction.message.author.send("Twój urlop został odrzucony.");
        return this.client.channels.get(ids.c3).send(logEmbed);
      }
      case "greenTick": {
        const logEmbed = new MessageEmbed()
          .setAuthor(reaction.message.author.tag, reaction.message.author.displayAvatarURL().replace(".webp", ".png"))
          .setColor("#00ff00")
          .setFooter("Zatwierdzony urlop")
          .addField("Osoba zatwierdzająca", user.tag);
        await reaction.message.reactions.removeAll();
        REGEXOD.lastIndex = null;
        REGEXDO.lastIndex = null;
        const regDataOd = REGEXOD.exec(reaction.message.content); //[cały string, dzień, miesiąc, rok]
        const regDataDo = REGEXDO.exec(reaction.message.content); //[cały string, dzień, miesiąc, rok]
        const dataOd = moment(new Date(`${regDataOd[3]}/${regDataOd[2]}/${regDataOd[1]}`)); //przyjmuje w formacie "rok/miesiąc/dzień"
        const dataDo = moment(new Date(`${regDataDo[3]}/${regDataDo[2]}/${regDataDo[1]}`)); //przyjmuje w formacie "rok/miesiąc/dzień"
        await sql.run(`UPDATE urlopy SET zatwierdzony = 'true' WHERE userId = ${reaction.message.author.id}`);
        if (dataOd.diff(dataDo) >= -1209600000) {
          await reaction.message.author.send("Twój urlop został przyjęty. 🏖").catch(() => undefined);
        } else {
          await reaction.message.author.send("Twój urlop został przyjęty, a Twoja ranga zostanie tymczasowo zdjęta aż do końca urlopu (zgodnie z regulaminem). 🏖").catch(() => undefined);
        }
        return this.client.channels.get(ids.c3).send(logEmbed);
      }
      }
      break;
    }
    case ids.c2: {
      const msg = reaction.message.channel.messages.get(ids.m1);
      if (reaction.message.id !== msg.id) return;
      switch (reaction.emoji.name) {
      case "greenTick": {
        const member = msg.guild.members.get(user.id);
        if (moment(member.joinedAt).diff(moment()) > -300000) {
          const _m = await msg.channel.send(`Ejejej, ${user.toString()}! Widzę co tam robisz, nawet 5 minut nie minęło odkąd dołączyłeś/aś tutaj! Nie ma szans byś w tak krótki okres czasu przeczytał(a) regulamin!`);
          await reaction.users.remove(user.id);
          await this.delayer(5e3);
          await _m.delete();
          return;
        }
        if (moment(member.user.createdAt).diff(moment()) > -604800000) {
          const _m = await msg.channel.send(`Przykro mi ${user.toString()}, ale Twoje konto na Discord musi mieć conajmniej tydzień. Spróbuj ponownie ${moment(member.user.createdAt).add(1, "week").fromNow()}!`);
          await reaction.users.remove(user.id);
          await this.delayer(5e3);
          await _m.delete();
          return;
        }
        await sql.run(`DELETE FROM zweryfikowani WHERE userId = ${member.id}`).catch(() => undefined);
        await sql.run("INSERT INTO zweryfikowani (userId, date) VALUES (?, ?)", [member.id, moment().unix()])
          .catch(async() => {
            await sql.run("CREATE TABLE IF NOT EXISTS zweryfikowani (userId TEXT, date INTEGER, ostatniaWiadomosc INTEGER)");
            await sql.run("INSERT INTO zweryfikowani (userId, date) VALUES (?, ?)", [member.id, moment().unix()]);
          });
        const _m = await msg.channel.send(`${member.toString()}, witamy w gronie zweryfikowanych! Główny kanał to <#${ids.c4}> btw.`);
        await member.roles.add(ids.r3);
        await reaction.users.remove(user.id);
        await _m.delete();
        return;
      }
      case "redTick": {
        await msg.guild.members.get(user.id).kick("Niezatwierdzenie regulaminu").catch(() => undefined);
        return reaction.users.remove(user.id);
      }
      }
    }
    }
  }

};