package org.psint.beyosclothing.modules.resellers.service.impl;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.psint.beyosclothing.modules.resellers.dto.request.AddBankAccountRequest;
import org.psint.beyosclothing.modules.resellers.dto.request.UpdateBankAccountRequest;
import org.psint.beyosclothing.modules.resellers.dto.response.BankAccountResponse;
import org.psint.beyosclothing.modules.resellers.entity.ResellerBankAccount;
import org.psint.beyosclothing.modules.resellers.repository.ResellerBankAccountRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ResellerBankAccountServiceImplTest {

    @Mock
    private ResellerBankAccountRepository bankAccountRepository;

    @InjectMocks
    private ResellerBankAccountServiceImpl bankAccountService;

    private ResellerBankAccount testBankAccount;
    private AddBankAccountRequest addRequest;
    private UpdateBankAccountRequest updateRequest;
    private Long resellerId;

    @BeforeEach
    void setUp() {
        resellerId = 1L;

        testBankAccount = ResellerBankAccount.builder()
                .id(1L)
                .uuid("bank-uuid-123")
                .resellerId(resellerId)
                .bankName("Test Bank")
                .accountHolderName("John Doe")
                .accountNumber("1234567890")
                .branchName("Main Branch")
                .branchCode("001")
                .swiftCode("TESTLKLX")
                .isPrimary(true)
                .isActive(true)
                .isVerified(false)
                .dateCreated(LocalDateTime.now().minusDays(10))
                .build();

        addRequest = AddBankAccountRequest.builder()
                .bankName("New Bank")
                .accountHolderName("Jane Doe")
                .accountNumber("9876543210")
                .branchName("Branch A")
                .branchCode("002")
                .swiftCode("NEWBANKX")
                .isPrimary(false)
                .build();

        updateRequest = UpdateBankAccountRequest.builder()
                .bankName("Updated Bank")
                .accountHolderName("Updated Name")
                .branchName("Updated Branch")
                .branchCode("003")
                .swiftCode("UPDATEDX")
                .isPrimary(true)
                .build();
    }

    @Test
    void addBankAccount() {
        when(bankAccountRepository.findByResellerIdAndIsActive(resellerId, true)).thenReturn(List.of());
        when(bankAccountRepository.save(any(ResellerBankAccount.class))).thenReturn(testBankAccount);

        BankAccountResponse response = bankAccountService.addBankAccount(resellerId, addRequest);

        assertNotNull(response);
        assertEquals("bank-uuid-123", response.getUuid());
        assertEquals("Test Bank", response.getBankName());
        assertEquals("John Doe", response.getAccountHolderName());
        assertEquals("****7890", response.getMaskedAccountNumber());
        assertNull(response.getAccountNumber());
        assertFalse(response.getIsVerified());
        assertTrue(response.getIsActive());

        verify(bankAccountRepository).findByResellerIdAndIsActive(resellerId, true);
        verify(bankAccountRepository).save(any(ResellerBankAccount.class));
    }

    @Test
    void addBankAccount_AsPrimary_UnsetsOtherPrimaryAccounts() {
        ResellerBankAccount existingPrimary = ResellerBankAccount.builder()
                .id(2L)
                .uuid("existing-uuid")
                .resellerId(resellerId)
                .bankName("Old Bank")
                .accountHolderName("Old Name")
                .accountNumber("1111111111")
                .isPrimary(true)
                .isActive(true)
                .isVerified(true)
                .build();

        AddBankAccountRequest primaryRequest = AddBankAccountRequest.builder()
                .bankName("New Primary Bank")
                .accountHolderName("New Name")
                .accountNumber("2222222222")
                .isPrimary(true)
                .build();

        when(bankAccountRepository.findByResellerIdAndIsActive(resellerId, true))
                .thenReturn(List.of(existingPrimary));
        when(bankAccountRepository.save(any(ResellerBankAccount.class))).thenReturn(testBankAccount);

        BankAccountResponse response = bankAccountService.addBankAccount(resellerId, primaryRequest);

        assertNotNull(response);
        verify(bankAccountRepository, times(2)).save(any(ResellerBankAccount.class));
    }

    @Test
    void updateBankAccount() {
        when(bankAccountRepository.findByResellerIdAndUuid(resellerId, "bank-uuid-123"))
                .thenReturn(Optional.of(testBankAccount));
        when(bankAccountRepository.findByResellerIdAndIsActive(resellerId, true)).thenReturn(List.of());
        when(bankAccountRepository.save(any(ResellerBankAccount.class))).thenReturn(testBankAccount);

        BankAccountResponse response = bankAccountService.updateBankAccount(resellerId, "bank-uuid-123", updateRequest);

        assertNotNull(response);
        assertEquals("bank-uuid-123", response.getUuid());


        verify(bankAccountRepository).findByResellerIdAndUuid(resellerId, "bank-uuid-123");
        verify(bankAccountRepository).save(testBankAccount);
    }

    @Test
    void updateBankAccount_WithNonExistentAccount_ThrowsException() {
        when(bankAccountRepository.findByResellerIdAndUuid(resellerId, "invalid-uuid"))
                .thenReturn(Optional.empty());

        assertThrows(IllegalArgumentException.class, () -> {
            bankAccountService.updateBankAccount(resellerId, "invalid-uuid", updateRequest);
        });

        verify(bankAccountRepository, never()).save(any());
    }

    @Test
    void updateBankAccount_AsPrimary_UnsetsOtherPrimaryAccounts() {
        ResellerBankAccount existingPrimary = ResellerBankAccount.builder()
                .id(2L)
                .uuid("other-uuid")
                .resellerId(resellerId)
                .bankName("Other Bank")
                .accountHolderName("Other Name")
                .accountNumber("3333333333")
                .isPrimary(true)
                .isActive(true)
                .build();

        when(bankAccountRepository.findByResellerIdAndUuid(resellerId, "bank-uuid-123"))
                .thenReturn(Optional.of(testBankAccount));
        when(bankAccountRepository.findByResellerIdAndIsActive(resellerId, true))
                .thenReturn(List.of(existingPrimary, testBankAccount));
        when(bankAccountRepository.save(any(ResellerBankAccount.class))).thenReturn(testBankAccount);

        bankAccountService.updateBankAccount(resellerId, "bank-uuid-123", updateRequest);

        verify(bankAccountRepository, times(2)).save(any(ResellerBankAccount.class));
    }

    @Test
    void deleteBankAccount() {
        when(bankAccountRepository.findByResellerIdAndUuid(resellerId, "bank-uuid-123"))
                .thenReturn(Optional.of(testBankAccount));
        when(bankAccountRepository.findByResellerIdAndIsActive(resellerId, true))
                .thenReturn(List.of());
        when(bankAccountRepository.save(any(ResellerBankAccount.class))).thenReturn(testBankAccount);

        bankAccountService.deleteBankAccount(resellerId, "bank-uuid-123");

        assertFalse(testBankAccount.getIsActive());
        verify(bankAccountRepository).findByResellerIdAndUuid(resellerId, "bank-uuid-123");
        verify(bankAccountRepository).save(testBankAccount);
    }

    @Test
    void deleteBankAccount_WithNonExistentAccount_ThrowsException() {
        when(bankAccountRepository.findByResellerIdAndUuid(resellerId, "invalid-uuid"))
                .thenReturn(Optional.empty());

        assertThrows(IllegalArgumentException.class, () -> {
            bankAccountService.deleteBankAccount(resellerId, "invalid-uuid");
        });

        verify(bankAccountRepository, never()).save(any());
    }

    @Test
    void deleteBankAccount_PrimaryAccount_SetsAnotherAsPrimary() {
        ResellerBankAccount anotherAccount = ResellerBankAccount.builder()
                .id(2L)
                .uuid("another-uuid")
                .resellerId(resellerId)
                .bankName("Another Bank")
                .accountHolderName("Another Name")
                .accountNumber("4444444444")
                .isPrimary(false)
                .isActive(true)
                .build();

        when(bankAccountRepository.findByResellerIdAndUuid(resellerId, "bank-uuid-123"))
                .thenReturn(Optional.of(testBankAccount));
        when(bankAccountRepository.findByResellerIdAndIsActive(resellerId, true))
                .thenReturn(List.of(anotherAccount));
        when(bankAccountRepository.save(any(ResellerBankAccount.class))).thenReturn(anotherAccount);

        bankAccountService.deleteBankAccount(resellerId, "bank-uuid-123");

        assertFalse(testBankAccount.getIsActive());
        verify(bankAccountRepository, times(2)).save(any(ResellerBankAccount.class));
    }

    @Test
    void getBankAccounts() {
        ResellerBankAccount secondAccount = ResellerBankAccount.builder()
                .id(2L)
                .uuid("second-uuid")
                .resellerId(resellerId)
                .bankName("Second Bank")
                .accountHolderName("Jane Doe")
                .accountNumber("5555555555")
                .isPrimary(false)
                .isActive(true)
                .isVerified(true)
                .build();

        when(bankAccountRepository.findByResellerIdAndIsActive(resellerId, true))
                .thenReturn(List.of(testBankAccount, secondAccount));

        List<BankAccountResponse> responses = bankAccountService.getBankAccounts(resellerId);

        assertNotNull(responses);
        assertEquals(2, responses.size());
        assertEquals("bank-uuid-123", responses.get(0).getUuid());
        assertEquals("second-uuid", responses.get(1).getUuid());
        assertEquals("****7890", responses.get(0).getMaskedAccountNumber());
        assertEquals("****5555", responses.get(1).getMaskedAccountNumber());

        verify(bankAccountRepository).findByResellerIdAndIsActive(resellerId, true);
    }

    @Test
    void getBankAccounts_NoAccounts_ReturnsEmptyList() {
        when(bankAccountRepository.findByResellerIdAndIsActive(resellerId, true))
                .thenReturn(List.of());

        List<BankAccountResponse> responses = bankAccountService.getBankAccounts(resellerId);

        assertNotNull(responses);
        assertTrue(responses.isEmpty());
    }

    @Test
    void getBankAccountByUuid() {
        when(bankAccountRepository.findByResellerIdAndUuid(resellerId, "bank-uuid-123"))
                .thenReturn(Optional.of(testBankAccount));

        BankAccountResponse response = bankAccountService.getBankAccountByUuid(resellerId, "bank-uuid-123");

        assertNotNull(response);
        assertEquals("bank-uuid-123", response.getUuid());
        assertEquals("Test Bank", response.getBankName());
        assertEquals("John Doe", response.getAccountHolderName());
        assertEquals("****7890", response.getMaskedAccountNumber());
        assertNull(response.getAccountNumber());
        assertTrue(response.getIsPrimary());

        verify(bankAccountRepository).findByResellerIdAndUuid(resellerId, "bank-uuid-123");
    }

    @Test
    void getBankAccountByUuid_WithNonExistentAccount_ThrowsException() {
        when(bankAccountRepository.findByResellerIdAndUuid(resellerId, "invalid-uuid"))
                .thenReturn(Optional.empty());

        assertThrows(IllegalArgumentException.class, () -> {
            bankAccountService.getBankAccountByUuid(resellerId, "invalid-uuid");
        });
    }

    @Test
    void setPrimaryAccount() {
        ResellerBankAccount oldPrimary = ResellerBankAccount.builder()
                .id(2L)
                .uuid("old-primary-uuid")
                .resellerId(resellerId)
                .bankName("Old Primary Bank")
                .accountHolderName("Old Name")
                .accountNumber("6666666666")
                .isPrimary(true)
                .isActive(true)
                .build();

        when(bankAccountRepository.findByResellerIdAndUuid(resellerId, "bank-uuid-123"))
                .thenReturn(Optional.of(testBankAccount));
        when(bankAccountRepository.findByResellerIdAndIsActive(resellerId, true))
                .thenReturn(List.of(oldPrimary, testBankAccount));
        when(bankAccountRepository.save(any(ResellerBankAccount.class))).thenReturn(testBankAccount);

        bankAccountService.setPrimaryAccount(resellerId, "bank-uuid-123");

        assertTrue(testBankAccount.getIsPrimary());
        verify(bankAccountRepository).findByResellerIdAndUuid(resellerId, "bank-uuid-123");
        verify(bankAccountRepository, times(2)).save(any(ResellerBankAccount.class));
    }

    @Test
    void setPrimaryAccount_WithNonExistentAccount_ThrowsException() {
        when(bankAccountRepository.findByResellerIdAndUuid(resellerId, "invalid-uuid"))
                .thenReturn(Optional.empty());

        assertThrows(IllegalArgumentException.class, () -> {
            bankAccountService.setPrimaryAccount(resellerId, "invalid-uuid");
        });

        verify(bankAccountRepository, never()).save(any());
    }
}
