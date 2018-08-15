const { Extendable } = require("klasa");

const REGEXOD = /(?:od:? ?)(0?[1-9]|[12]\d|3[01])(?:\.|\/|-)(0?[1-9]|1[0-2])(?:\.|\/|-)(19|20\d{2})/img;
const REGEXDO = /(?:do:? ?)(0?[1-9]|[12]\d|3[01])(?:\.|\/|-)(0?[1-9]|1[0-2])(?:\.|\/|-)(19|20\d{2})/img;

module.exports = class extends Extendable {

  constructor(...args) {
    super(...args, {
      enabled: true,
      appliesTo: ["Message"]
    });
  }

  get extend() {
    return REGEXOD.test(this.content) && REGEXDO.test(this.content);
  }

};
