const { Event } = require("klasa");

module.exports = class extends Event {

  constructor(...args) {
    super(...args, {
      enabled: true
    });
  }

  async run(packet) {
    if (!packet.t || !packet.t.startsWith("MESSAGE_REACTION")) return;

    const channel = this.client.channels.get(packet.d.channel_id);
    if (channel.messages.has(packet.d.message_id)) return;

    let message;
    try {
      message = await channel.messages.fetch(packet.d.message_id);
    } catch (err) {
      return;
    }

    if (packet.t === "MESSAGE_REACTION_REMOVE_ALL") {
      this.client.emit("messageReactionRemoveAll", message);
      return;
    }

    // if (packet.d.emoji.name !== "🗑") return;
    const user = await this.client.users.fetch(packet.d.user_id);

    if (packet.t === "MESSAGE_REACTION_ADD") {
      this.client.emit("messageReactionAdd", message.reactions.get(packet.d.emoji.id || packet.d.emoji.name), user);
    } else if (packet.t === "MESSAGE_REACTION_REMOVE") {
      this.client.emit("messageReactionRemove", {
        message,
        emoji: packet.d.emoji
      }, user);
    }
  }

};