package org.psint.beyosclothing.modules.resellers.service.impl;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.psint.beyosclothing.modules.resellers.dto.response.AdminResellerDetailResponse;
import org.psint.beyosclothing.modules.resellers.dto.response.AdminResellerListResponse;
import org.psint.beyosclothing.modules.resellers.dto.response.WithdrawalListResponse;
import org.psint.beyosclothing.modules.resellers.entity.*;
import org.psint.beyosclothing.modules.resellers.repository.ResellerBankAccountRepository;
import org.psint.beyosclothing.modules.resellers.repository.ResellerRepository;
import org.psint.beyosclothing.modules.resellers.repository.ResellerWalletTransactionRepository;
import org.psint.beyosclothing.modules.resellers.repository.ResellerWithdrawalRequestRepository;
import org.psint.beyosclothing.modules.resellers.service.ResellerWalletService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AdminResellerServiceImplTest {

    @Mock
    private ResellerRepository resellerRepository;

    @Mock
    private ResellerWithdrawalRequestRepository withdrawalRepository;

    @Mock
    private ResellerBankAccountRepository bankAccountRepository;

    @Mock
    private ResellerWalletService walletService;

    @Mock
    private ResellerWalletTransactionRepository transactionRepository;

    @InjectMocks
    private AdminResellerServiceImpl adminResellerService;

    private Reseller testReseller;
    private ResellerWithdrawalRequest testWithdrawal;
    private ResellerBankAccount testBankAccount;
    private Pageable pageable;

    @BeforeEach
    void setUp() {
        pageable = PageRequest.of(0, 10);

        testReseller = Reseller.builder()
                .id(1L)
                .uuid("test-uuid-123")
                .userId(100L)
                .firstName("John")
                .lastName("Doe")
                .email("john.doe@example.com")
                .phone("+1234567890")
                .addressLine1("123 Main St")
                .city("Test City")
                .district("Test District")
                .province("Test Province")
                .postalCode("12345")
                .status(ResellerStatus.APPROVED)
                .allowPriceOverride(true)
                .minAllowedMarkupPct(new BigDecimal("10.00"))
                .maxAllowedMarkupPct(new BigDecimal("50.00"))
                .creditBalance(new BigDecimal("1000.00"))
                .creditLimit(new BigDecimal("5000.00"))
                .isActive(true)
                .dateCreated(LocalDateTime.now().minusDays(30))
                .build();

        testWithdrawal = ResellerWithdrawalRequest.builder()
                .id(1L)
                .uuid("withdrawal-uuid-456")
                .resellerId(1L)
                .bankAccountId(10L)
                .amount(new BigDecimal("500.00"))
                .status(WithdrawalStatus.PENDING)
                .requestedDate(LocalDateTime.now().minusDays(1))
                .build();

        testBankAccount = ResellerBankAccount.builder()
                .id(10L)
                .uuid("bank-uuid-789")
                .resellerId(1L)
                .bankName("Test Bank")
                .accountNumber("1234567890")
                .accountHolderName("John Doe")
                .isPrimary(true)
                .isActive(true)
                .build();
    }

    @Test
    void getAllResellers() {
        Page<Reseller> resellerPage = new PageImpl<>(List.of(testReseller), pageable, 1);
        when(resellerRepository.findAll(pageable)).thenReturn(resellerPage);

        AdminResellerListResponse response = adminResellerService.getAllResellers(pageable, null);

        assertNotNull(response);
        assertEquals(1, response.getResellers().size());
        assertEquals(1, response.getTotalResellers());
        assertEquals(0, response.getCurrentPage());
        assertEquals(1, response.getTotalPages());
        assertEquals(10, response.getPageSize());

        AdminResellerListResponse.ResellerSummary summary = response.getResellers().get(0);
        assertEquals("test-uuid-123", summary.getUuid());
        assertEquals("John Doe", summary.getFullName());
        assertEquals("john.doe@example.com", summary.getEmail());
        assertEquals("+1234567890", summary.getPhone());
        assertEquals("APPROVED", summary.getStatus());
        assertEquals(new BigDecimal("1000.00"), summary.getCurrentBalance());
        assertTrue(summary.getIsActive());

        verify(resellerRepository).findAll(pageable);
    }

    @Test
    void getAllResellers_WithValidStatusFilter_ReturnsFilteredResellers() {
        Page<Reseller> resellerPage = new PageImpl<>(List.of(testReseller), pageable, 1);
        when(resellerRepository.findByStatus(ResellerStatus.APPROVED, pageable)).thenReturn(resellerPage);

        AdminResellerListResponse response = adminResellerService.getAllResellers(pageable, "APPROVED");

        assertNotNull(response);
        assertEquals(1, response.getResellers().size());
        verify(resellerRepository).findByStatus(ResellerStatus.APPROVED, pageable);
    }

    @Test
    void getAllResellers_WithInvalidStatusFilter_ReturnsAllResellers() {
        Page<Reseller> resellerPage = new PageImpl<>(List.of(testReseller), pageable, 1);
        when(resellerRepository.findAll(pageable)).thenReturn(resellerPage);

        AdminResellerListResponse response = adminResellerService.getAllResellers(pageable, "INVALID_STATUS");

        assertNotNull(response);
        assertEquals(1, response.getResellers().size());
        verify(resellerRepository).findAll(pageable);
    }

    @Test
    void getAllResellers_FiltersOutInactiveResellers() {
        Reseller inactiveReseller = Reseller.builder()
                .id(2L)
                .uuid("inactive-uuid")
                .email("inactive@example.com")
                .status(ResellerStatus.APPROVED)
                .isActive(false)
                .creditBalance(BigDecimal.ZERO)
                .build();

        Page<Reseller> resellerPage = new PageImpl<>(List.of(testReseller, inactiveReseller), pageable, 2);
        when(resellerRepository.findAll(pageable)).thenReturn(resellerPage);

        AdminResellerListResponse response = adminResellerService.getAllResellers(pageable, null);

        assertNotNull(response);
        assertEquals(1, response.getResellers().size());
        assertEquals("test-uuid-123", response.getResellers().get(0).getUuid());
    }

    @Test
    void getAllResellers_WithEmptyPage_ReturnsEmptyList() {
        Page<Reseller> emptyPage = new PageImpl<>(Collections.emptyList(), pageable, 0);
        when(resellerRepository.findAll(pageable)).thenReturn(emptyPage);

        AdminResellerListResponse response = adminResellerService.getAllResellers(pageable, null);

        assertNotNull(response);
        assertTrue(response.getResellers().isEmpty());
        assertEquals(0, response.getTotalResellers());
    }

    @Test
    void getResellerDetails() {
        when(resellerRepository.findByUuid("test-uuid-123")).thenReturn(Optional.of(testReseller));

        AdminResellerDetailResponse response = adminResellerService.getResellerDetails("test-uuid-123");

        assertNotNull(response);
        assertNotNull(response.getProfile());
        assertEquals("test-uuid-123", response.getProfile().getUuid());
        assertEquals("John Doe", response.getProfile().getFullName());
        assertEquals("john.doe@example.com", response.getProfile().getEmail());
        assertEquals("+1234567890", response.getProfile().getPhone());
        assertEquals("APPROVED", response.getProfile().getStatus());
        assertTrue(response.getProfile().getIsActive());
        assertTrue(response.getProfile().getAllowPriceOverride());
        assertEquals(new BigDecimal("10.00"), response.getProfile().getMinMarkupPct());
        assertEquals(new BigDecimal("50.00"), response.getProfile().getMaxMarkupPct());

        assertNotNull(response.getMetrics());
        assertEquals(0L, response.getMetrics().getTotalOrders());
        assertEquals(BigDecimal.ZERO, response.getMetrics().getTotalSales());

        assertNotNull(response.getWallet());
        assertEquals(new BigDecimal("1000.00"), response.getWallet().getCurrentBalance());
        assertEquals(new BigDecimal("5000.00"), response.getWallet().getCreditLimit());
        assertEquals(new BigDecimal("4000.00"), response.getWallet().getAvailableCredit());

        verify(resellerRepository).findByUuid("test-uuid-123");
    }

    @Test
    void getResellerDetails_WithInvalidUuid_ThrowsException() {
        when(resellerRepository.findByUuid("invalid-uuid")).thenReturn(Optional.empty());

        assertThrows(IllegalArgumentException.class, () -> {
            adminResellerService.getResellerDetails("invalid-uuid");
        });

        verify(resellerRepository).findByUuid("invalid-uuid");
    }

    @Test
    void getWithdrawals() {
        Page<ResellerWithdrawalRequest> withdrawalPage = new PageImpl<>(List.of(testWithdrawal), pageable, 1);
        when(withdrawalRepository.findByStatus(WithdrawalStatus.PENDING, pageable)).thenReturn(withdrawalPage);
        when(bankAccountRepository.findById(10L)).thenReturn(Optional.of(testBankAccount));

        WithdrawalListResponse response = adminResellerService.getWithdrawals(pageable);

        assertNotNull(response);
        assertEquals(1, response.getWithdrawals().size());
        assertEquals(1, response.getTotalWithdrawals());
        assertEquals(0, response.getCurrentPage());

        WithdrawalListResponse.WithdrawalSummary summary = response.getWithdrawals().get(0);
        assertEquals("withdrawal-uuid-456", summary.getUuid());
        assertEquals(new BigDecimal("500.00"), summary.getAmount());
        assertEquals("PENDING", summary.getStatus());
        assertEquals("Test Bank", summary.getBankName());
        assertEquals("1234567890", summary.getAccountNumber());
        assertEquals("John Doe", summary.getAccountHolderName());

        verify(withdrawalRepository).findByStatus(WithdrawalStatus.PENDING, pageable);
        verify(bankAccountRepository).findById(10L);
    }

    @Test
    void getWithdrawals_WithMissingBankAccount_UsesDefaultValues() {
        Page<ResellerWithdrawalRequest> withdrawalPage = new PageImpl<>(List.of(testWithdrawal), pageable, 1);
        when(withdrawalRepository.findByStatus(WithdrawalStatus.PENDING, pageable)).thenReturn(withdrawalPage);
        when(bankAccountRepository.findById(10L)).thenReturn(Optional.empty());

        WithdrawalListResponse response = adminResellerService.getWithdrawals(pageable);

        assertNotNull(response);
        assertEquals(1, response.getWithdrawals().size());

        WithdrawalListResponse.WithdrawalSummary summary = response.getWithdrawals().get(0);
        assertEquals("Unknown", summary.getBankName());
        assertNull(summary.getAccountNumber());
        assertNull(summary.getAccountHolderName());
    }

    @Test
    void getWithdrawals_WithEmptyPage_ReturnsEmptyList() {
        Page<ResellerWithdrawalRequest> emptyPage = new PageImpl<>(Collections.emptyList(), pageable, 0);
        when(withdrawalRepository.findByStatus(WithdrawalStatus.PENDING, pageable)).thenReturn(emptyPage);

        WithdrawalListResponse response = adminResellerService.getWithdrawals(pageable);

        assertNotNull(response);
        assertTrue(response.getWithdrawals().isEmpty());
        assertEquals(0, response.getTotalWithdrawals());
    }

    @Test
    void getAllWithdrawals() {
        Page<ResellerWithdrawalRequest> withdrawalPage = new PageImpl<>(List.of(testWithdrawal), pageable, 1);
        when(withdrawalRepository.findAll(pageable)).thenReturn(withdrawalPage);
        when(bankAccountRepository.findById(10L)).thenReturn(Optional.of(testBankAccount));

        WithdrawalListResponse response = adminResellerService.getAllWithdrawals(pageable);

        assertNotNull(response);
        assertEquals(1, response.getWithdrawals().size());
        assertEquals(1, response.getTotalWithdrawals());

        verify(withdrawalRepository).findAll(pageable);
    }

    @Test
    void updateWithdrawalStatus() {
        ResellerWalletTransaction debitTx = ResellerWalletTransaction.builder()
                .id(1L)
                .resellerId(1L)
                .referenceWithdrawalId(1L)
                .type(TransactionType.WITHDRAWAL_DEBIT)
                .status(WalletTransactionStatus.PENDING)
                .amount(new BigDecimal("500.00"))
                .build();

        when(withdrawalRepository.findByUuid("withdrawal-uuid-456")).thenReturn(Optional.of(testWithdrawal));
        when(transactionRepository.findFirstByReferenceWithdrawalIdAndTypeOrderByDateCreatedDesc(
                1L, TransactionType.WITHDRAWAL_DEBIT)).thenReturn(debitTx);

        adminResellerService.updateWithdrawalStatus("withdrawal-uuid-456", "APPROVED", 999L, "TXN-REF-123");

        assertEquals(WithdrawalStatus.APPROVED, testWithdrawal.getStatus());
        assertEquals(999L, testWithdrawal.getProcessedByAdminId());
        assertNotNull(testWithdrawal.getProcessedDate());
        assertEquals("TXN-REF-123", testWithdrawal.getAdminNotes());
        assertEquals("TXN-REF-123", testWithdrawal.getTransactionReference());

        verify(withdrawalRepository).findByUuid("withdrawal-uuid-456");
        verify(transactionRepository).findFirstByReferenceWithdrawalIdAndTypeOrderByDateCreatedDesc(
                1L, TransactionType.WITHDRAWAL_DEBIT);
        verify(transactionRepository).save(debitTx);
        assertEquals(WalletTransactionStatus.SUCCESS, debitTx.getStatus());
        verify(withdrawalRepository).save(testWithdrawal);
    }

    @Test
    void updateWithdrawalStatus_ToRejected_UpdatesStatusAndRefundsWallet() {
        when(withdrawalRepository.findByUuid("withdrawal-uuid-456")).thenReturn(Optional.of(testWithdrawal));

        adminResellerService.updateWithdrawalStatus("withdrawal-uuid-456", "REJECTED", 999L, "Insufficient funds");

        assertEquals(WithdrawalStatus.REJECTED, testWithdrawal.getStatus());
        assertEquals(999L, testWithdrawal.getProcessedByAdminId());
        assertNotNull(testWithdrawal.getProcessedDate());
        assertEquals("Insufficient funds", testWithdrawal.getAdminNotes());
        assertEquals("Insufficient funds", testWithdrawal.getRejectionReason());

        verify(withdrawalRepository).findByUuid("withdrawal-uuid-456");
        verify(walletService).reverseWithdrawal(1L, new BigDecimal("500.00"), 1L);
        verify(withdrawalRepository).save(testWithdrawal);
    }

    @Test
    void updateWithdrawalStatus_WithInvalidStatus_ThrowsException() {
        assertThrows(IllegalArgumentException.class, () -> {
            adminResellerService.updateWithdrawalStatus("withdrawal-uuid-456", "INVALID", 999L, "notes");
        });

        verify(withdrawalRepository, never()).findByUuid(any());
        verify(withdrawalRepository, never()).save(any());
        verify(walletService, never()).reverseWithdrawal(any(), any(), any());
    }

    @Test
    void updateWithdrawalStatus_WithNonExistentWithdrawal_ThrowsException() {
        when(withdrawalRepository.findByUuid("non-existent-uuid")).thenReturn(Optional.empty());

        assertThrows(IllegalArgumentException.class, () -> {
            adminResellerService.updateWithdrawalStatus("non-existent-uuid", "APPROVED", 999L, "notes");
        });

        verify(withdrawalRepository).findByUuid("non-existent-uuid");
        verify(withdrawalRepository, never()).save(any());
    }

    @Test
    void updateWithdrawalStatus_WithCaseInsensitiveStatus_AcceptsLowerCase() {
        when(withdrawalRepository.findByUuid("withdrawal-uuid-456")).thenReturn(Optional.of(testWithdrawal));

        adminResellerService.updateWithdrawalStatus("withdrawal-uuid-456", "approved", 999L, "notes");

        assertEquals(WithdrawalStatus.APPROVED, testWithdrawal.getStatus());
        verify(withdrawalRepository).save(testWithdrawal);
    }

    @Test
    void updateWithdrawalStatus_ToApproved_WithoutDebitTransaction_DoesNotFail() {
        when(withdrawalRepository.findByUuid("withdrawal-uuid-456")).thenReturn(Optional.of(testWithdrawal));
        when(transactionRepository.findFirstByReferenceWithdrawalIdAndTypeOrderByDateCreatedDesc(
                1L, TransactionType.WITHDRAWAL_DEBIT)).thenReturn(null);

        adminResellerService.updateWithdrawalStatus("withdrawal-uuid-456", "APPROVED", 999L, "TXN-REF-123");

        assertEquals(WithdrawalStatus.APPROVED, testWithdrawal.getStatus());
        verify(transactionRepository, never()).save(any());
        verify(withdrawalRepository).save(testWithdrawal);
    }
}
