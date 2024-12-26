package de.lenneflow.lenneflowterraformserver.repository;

import de.lenneflow.lenneflowterraformserver.model.AccessToken;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.dao.InvalidDataAccessApiUsageException;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AccessTokenRepositoryTest {

    @Mock
    private AccessTokenRepository accessTokenRepository;

    @InjectMocks
    private AccessTokenRepositoryTest accessTokenRepositoryTest;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
    }

    @Test
    void findByUidReturnsAccessTokenWhenExists() {
        AccessToken token = new AccessToken();
        token.setUid("test-uid");
        token.setExpiration(LocalDateTime.now().plusDays(1));

        when(accessTokenRepository.findByUid("test-uid")).thenReturn(token);

        AccessToken foundToken = accessTokenRepository.findByUid("test-uid");

        assertNotNull(foundToken);
        assertEquals("test-uid", foundToken.getUid());
    }

    @Test
    void findByUidReturnsNullWhenTokenDoesNotExist() {
        when(accessTokenRepository.findByUid("non-existent-uid")).thenReturn(null);

        AccessToken foundToken = accessTokenRepository.findByUid("non-existent-uid");

        assertNull(foundToken);
    }

    @Test
    void saveAccessTokenSuccessfully() {
        AccessToken token = new AccessToken();
        token.setUid("test-uid");
        token.setExpiration(LocalDateTime.now().plusDays(1));

        when(accessTokenRepository.save(token)).thenReturn(token);

        AccessToken savedToken = accessTokenRepository.save(token);

        assertNotNull(savedToken);
        assertEquals("test-uid", savedToken.getUid());
    }

    @Test
    void deleteAccessTokenSuccessfully() {
        AccessToken token = new AccessToken();
        token.setUid("test-uid");
        token.setExpiration(LocalDateTime.now().plusDays(1));

        doNothing().when(accessTokenRepository).delete(token);

        accessTokenRepository.delete(token);

        verify(accessTokenRepository, times(1)).delete(token);
    }
}