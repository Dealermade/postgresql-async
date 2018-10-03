package com.github.dealermade.async.db.exceptions

import com.github.dealermade.async.db.Connection

class ConnectionTimeoutedException(val connection: Connection)
    extends DatabaseException("The connection %s has a timeouted query and is being closed".format(connection))
