package com.edu.common.config;

import org.junit.jupiter.api.Test;
import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.Statement;
import java.sql.SQLException;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

class DatabaseWarmupConfigurationTest {
    @Test
    void establishesDatabaseBeforeReadyAndClosesResources() throws Exception {
        DataSource source = mock(DataSource.class);
        Connection connection = mock(Connection.class);
        Statement statement = mock(Statement.class);
        when(source.getConnection()).thenReturn(connection);
        when(connection.createStatement()).thenReturn(statement);
        new DatabaseWarmupConfiguration().databaseWarmup(source).run(null);
        verify(statement).execute("SELECT 1");
        verify(statement).close();
        verify(connection).close();
    }
    @Test
    void failedDatabaseCannotBecomeReady() throws Exception {
        DataSource source = mock(DataSource.class);
        when(source.getConnection()).thenThrow(new SQLException("unavailable"));
        assertThrows(SQLException.class, () -> new DatabaseWarmupConfiguration().databaseWarmup(source).run(null));
    }
}
