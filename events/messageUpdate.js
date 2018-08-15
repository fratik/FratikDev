const { Event } = require("klasa");
const sql = require("sqlite");
sql.open("./dane.sqlite");
const moment = require("moment");
module.exports = class extends Event {

  constructor(...args) {
    super(...args, {
      enabled: true
    });
  }

  async run(oMsg, nMsg) {
    switch (oMsg) {
    case "424310954507894784": {
      if (oMsg.content === nMsg.content) return;
      await oMsg.channel.send(`${oMsg.author.toString()}: Nie można edytować urlopów! Usuwam Twój urlop!`);
      await oMsg.delete();
      await sql.run(`DELETE FROM urlopy WHERE userId = ${oMsg.author.id}`).catch(() => undefined);
      await sql.run("INSERT INTO cooldowny (userId, until) VALUES (?, ?)", [oMsg.author.id, moment(new Date().setHours(0, 0, 0, 0)).add(2, "weeks").unix()])
        .catch(async() => {
          await sql.run("CREATE TABLE IF NOT EXISTS cooldowny (userId TEXT, until INTEGER)");
          await sql.run("INSERT INTO cooldowny (userId, until) VALUES (?, ?)", [oMsg.author.id, moment(new Date().setHours(0, 0, 0, 0)).add(2, "weeks").unix()]);
        });
    }
    }
  }

};
