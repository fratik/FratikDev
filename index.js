const { Client } = require("klasa");
const config = require("./config.json");
process.stdin.resume();
process.once("SIGINT", async() => {
  try {
    if (client && client.console) client.console.log("Zamykam bazę danych..");
    else console.log("Zamykam bazę danych..");
    client.emit("error", "Otrzymano SIGINT. Zamykam instancję.");
    for (const i of client._intervals) clearInterval(i);
    client._intervals.clear();
    client.ready = false;
    await client.user.setPresence({status: "dnd", activity: { name: "Wyłączanie.." }});
    await client.destroy();
    process.exit(0);
  } catch (err) {
    console.error(err);
    process.exit(0);
  }
});

class FratikDev extends Client {

  constructor() {
    super({
      messageCacheLifetime: 0,
      messageSweepInterval: 0,
      messageCacheMaxSize: 400,
      disabledEvents: [],
      ownerID: "267761613438713876",
      prefix: config.prefix,
      customPromptDefaults: {
        cmdPrompt: true,
        promptTime: 30000,
        promptLimit: 5
      },
      pieceDefaults: {
        commands: {
          promptLimit: 5,
          usageDelim: " "
        }
      },
      cmdLogging: true,
      language: "pl-PL"
    });

  }

}

const client = new FratikDev();

client.login(config.token);
module.exports = client;