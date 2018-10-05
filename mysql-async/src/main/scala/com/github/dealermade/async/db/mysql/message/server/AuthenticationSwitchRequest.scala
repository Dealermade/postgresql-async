package com.github.dealermade.async.db.mysql.message.server

case class AuthenticationSwitchRequest(method: String, seed: String) extends ServerMessage(ServerMessage.EOF)
