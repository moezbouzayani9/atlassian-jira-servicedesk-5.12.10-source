package com.atlassian.servicedesk.plugins.base.internal.util.security;

import com.atlassian.servicedesk.plugins.base.internal.api.util.security.ServiceDeskCryptoException;
import com.google.crypto.tink.CleartextKeysetHandle;
import com.google.crypto.tink.JsonKeysetWriter;
import com.google.crypto.tink.KeysetHandle;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.security.GeneralSecurityException;
import java.util.UUID;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.mockito.junit.MockitoJUnitRunner;

import static com.atlassian.servicedesk.plugins.base.internal.util.security.ServiceDeskEncryptorImpl.KEY_TEMPLATE;
import static org.apache.commons.lang3.RandomStringUtils.randomAlphanumeric;
import static org.hamcrest.Matchers.emptyString;
import static org.hamcrest.core.Is.is;
import static org.hamcrest.core.IsNot.not;
import static org.junit.Assert.assertThat;

@RunWith(MockitoJUnitRunner.StrictStubs.class)
public class ServiceDeskEncryptorImplTest {

    @Rule
    public TemporaryFolder tmp = new TemporaryFolder();

    private ServiceDeskEncryptorImpl serviceDeskEncryptor;

    private static final String SAMPLE_API_KEY = UUID.randomUUID().toString();
    private static final String EMPTY_KEY = "";

    @Before
    public void setup() {
        serviceDeskEncryptor = new ServiceDeskEncryptorImpl();
    }

    @Test
    public void encryptKey__success() throws ServiceDeskCryptoException {
        final String fileName = randomAlphanumeric(5);
        final String filePath = tmp.getRoot().getPath() + "/keypath/" + fileName;

        final String encryptedKey = serviceDeskEncryptor.encryptKey(SAMPLE_API_KEY, filePath);

        assertThat("Encrypted key is not empty", encryptedKey, not(emptyString()));
        assertThat("Encrypted key is not same as original Key", encryptedKey, not(SAMPLE_API_KEY));
    }

    @Test
    public void encryptKey__existing_key_file_success() throws ServiceDeskCryptoException, GeneralSecurityException, IOException {
        final String filePath = tmp.newFile().getPath();
        CleartextKeysetHandle.write(KeysetHandle.generateNew(KEY_TEMPLATE), JsonKeysetWriter.withPath(filePath));
        final String originalKeySet = new String(Files.readAllBytes(Paths.get(filePath)), StandardCharsets.UTF_8);

        final String encryptedKey = serviceDeskEncryptor.encryptKey(SAMPLE_API_KEY, filePath);
        final String keySetAfterTest = new String(Files.readAllBytes(Paths.get(filePath)), StandardCharsets.UTF_8);

        assertThat("Encrypted key is not empty", encryptedKey, not(emptyString()));
        assertThat("Encrypted key is not same as original Key", encryptedKey, not(SAMPLE_API_KEY));
        assertThat("Keyset file should not have been overwritten", keySetAfterTest, is(originalKeySet));
    }

    @Test
    public void encryptKey__empty_key_success() throws ServiceDeskCryptoException {
        final String fileName = randomAlphanumeric(5);
        final String filePath = tmp.getRoot().getPath() + "/keypath/" + fileName;

        final String encryptedKey = serviceDeskEncryptor.encryptKey(EMPTY_KEY, filePath);

        assertThat("Encrypted key is not empty", encryptedKey, not(emptyString()));
        assertThat("Encrypted key is not same as original Key", encryptedKey, not(EMPTY_KEY));
    }

    @Test(expected = ServiceDeskCryptoException.class)
    public void encryptKey__invalid_file_failure() throws ServiceDeskCryptoException, IOException {
        final String folderName = tmp.newFile().getPath();

        serviceDeskEncryptor.encryptKey(EMPTY_KEY, folderName);
    }

    @Test
    public void decryptKey__success() throws ServiceDeskCryptoException {
        final String fileName = randomAlphanumeric(5);
        final String filePath = tmp.getRoot().getPath() + "/keypath/" + fileName;

        final String encryptedKey = serviceDeskEncryptor.encryptKey(SAMPLE_API_KEY, filePath);

        final String decryptedKey = serviceDeskEncryptor.decryptKey(encryptedKey, filePath);

        assertThat("Decrypted key is not empty", decryptedKey, not(emptyString()));
        assertThat("Decrypted key is same as original Key", decryptedKey, is(SAMPLE_API_KEY));
    }

    @Test(expected = ServiceDeskCryptoException.class)
    public void decryptKey__new_key_failure() throws ServiceDeskCryptoException, GeneralSecurityException, IOException {
        final String fileName = randomAlphanumeric(5);
        final String filePath = tmp.getRoot().getPath() + "/keypath/" + fileName;
        final String encryptedKey = serviceDeskEncryptor.encryptKey(SAMPLE_API_KEY, filePath);
        //Overwrite existing key
        CleartextKeysetHandle.write(KeysetHandle.generateNew(KEY_TEMPLATE), JsonKeysetWriter.withPath(filePath));

        final String decryptedKey = serviceDeskEncryptor.decryptKey(encryptedKey, filePath);

        assertThat("Decrypted key is not empty", decryptedKey, not(emptyString()));
        assertThat("Decrypted key is same as original Key", decryptedKey, is(SAMPLE_API_KEY));
    }

    @Test(expected = ServiceDeskCryptoException.class)
    public void decryptKey__invalid_file_failure() throws ServiceDeskCryptoException, IOException {
        final String folderName = tmp.newFile().getPath();

        serviceDeskEncryptor.decryptKey(EMPTY_KEY, folderName);
    }
}
