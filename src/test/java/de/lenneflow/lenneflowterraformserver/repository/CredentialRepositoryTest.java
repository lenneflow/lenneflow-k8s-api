package de.lenneflow.lenneflowterraformserver.repository;

import de.lenneflow.lenneflowterraformserver.model.Credential;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.dao.InvalidDataAccessApiUsageException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class CredentialRepositoryTest {

    @Mock
    private CredentialRepository credentialRepository;

    @InjectMocks
    private CredentialRepositoryTest credentialRepositoryTest;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
    }

    @Test
    void findByUidReturnsCredentialWhenExists() {
        Credential credential = new Credential();
        credential.setUid("test-uid");

        when(credentialRepository.findByUid("test-uid")).thenReturn(credential);

        Credential foundCredential = credentialRepository.findByUid("test-uid");

        assertNotNull(foundCredential);
        assertEquals("test-uid", foundCredential.getUid());
    }

    @Test
    void findByUidReturnsNullWhenCredentialDoesNotExist() {
        when(credentialRepository.findByUid("non-existent-uid")).thenReturn(null);

        Credential foundCredential = credentialRepository.findByUid("non-existent-uid");

        assertNull(foundCredential);
    }

    @Test
    void saveCredentialSuccessfully() {
        Credential credential = new Credential();
        credential.setUid("test-uid");

        when(credentialRepository.save(credential)).thenReturn(credential);

        Credential savedCredential = credentialRepository.save(credential);

        assertNotNull(savedCredential);
        assertEquals("test-uid", savedCredential.getUid());
    }

    @Test
    void deleteCredentialSuccessfully() {
        Credential credential = new Credential();
        credential.setUid("test-uid");

        doNothing().when(credentialRepository).delete(credential);

        credentialRepository.delete(credential);

        verify(credentialRepository, times(1)).delete(credential);
    }
}