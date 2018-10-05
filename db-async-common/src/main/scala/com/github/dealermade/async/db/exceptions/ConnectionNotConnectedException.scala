package com.github.dealermade.async.db.exceptions

import com.github.dealermade.async.db.Connection

class ConnectionNotConnectedException(val connection: Connection)
    extends DatabaseException("The connection %s is not connected to the database".format(connection))
