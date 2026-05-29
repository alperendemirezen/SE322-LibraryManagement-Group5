package com.example.library.unit;

import com.example.library.dto.BorrowResponse;
import com.example.library.exception.BookNotAvailableException;
import com.example.library.exception.BorrowLimitExceededException;
import com.example.library.exception.MemberNotFoundException;
import com.example.library.model.Book;
import com.example.library.model.BorrowRecord;
import com.example.library.model.BorrowStatus;
import com.example.library.model.Genre;
import com.example.library.model.Member;
import com.example.library.model.MembershipType;
import com.example.library.repository.BookRepository;
import com.example.library.repository.BorrowRecordRepository;
import com.example.library.repository.MemberRepository;
import com.example.library.service.BorrowService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BorrowServiceTest {

    @Mock
    private BorrowRecordRepository borrowRecordRepository;

    @Mock
    private BookRepository bookRepository;

    @Mock
    private MemberRepository memberRepository;

    @InjectMocks
    private BorrowService borrowService;

    private Book sampleBook;
    private Member sampleMember;

    @BeforeEach
    void setUp() {
        sampleBook = new Book("978-0-13-468599-1", "Clean Code", "Robert C. Martin", 3, Genre.TECHNOLOGY);
        sampleBook.setId(1L);
        sampleBook.setAvailableCopies(3);

        sampleMember = new Member("Alice", "alice@example.com", MembershipType.STANDARD);
        sampleMember.setId(1L);
    }

    @Nested
    @DisplayName("borrowBook()")
    class BorrowBookTests {

        @Test
        @DisplayName("should successfully borrow a book when all conditions are met")
        void shouldBorrowBook_WhenAllConditionsMet() {
            when(memberRepository.findById(1L)).thenReturn(Optional.of(sampleMember));
            when(bookRepository.findById(1L)).thenReturn(Optional.of(sampleBook));
            when(borrowRecordRepository.countActiveBorrowsByMember(1L)).thenReturn(0);
            when(borrowRecordRepository.existsByBookIdAndMemberIdAndStatus(1L, 1L, BorrowStatus.BORROWED))
                    .thenReturn(false);
            when(borrowRecordRepository.save(any(BorrowRecord.class)))
                    .thenAnswer(invocation -> {
                        BorrowRecord record = invocation.getArgument(0);
                        record.setId(1L);
                        return record;
                    });
            when(bookRepository.save(any(Book.class))).thenReturn(sampleBook);

            BorrowResponse response = borrowService.borrowBook(1L, 1L);

            assertNotNull(response);
            assertEquals("Clean Code", response.getBookTitle());
            assertEquals("Alice", response.getMemberName());
            assertEquals(BorrowStatus.BORROWED, response.getStatus());
            verify(borrowRecordRepository).save(any(BorrowRecord.class));
            verify(bookRepository).save(any(Book.class));
        }

        @Test
        @DisplayName("should throw MemberNotFoundException when member does not exist")
        void shouldThrow_WhenMemberNotFound() {
            when(memberRepository.findById(99L)).thenReturn(Optional.empty());

            assertThrows(MemberNotFoundException.class, () -> borrowService.borrowBook(1L, 99L));
            verify(borrowRecordRepository, never()).save(any());
        }

        @Test
        @DisplayName("should throw when book has no available copies")
        void shouldThrow_WhenNoAvailableCopies() {
            sampleBook.setAvailableCopies(0);
            when(memberRepository.findById(1L)).thenReturn(Optional.of(sampleMember));
            when(bookRepository.findById(1L)).thenReturn(Optional.of(sampleBook));

            assertThrows(BookNotAvailableException.class, () -> borrowService.borrowBook(1L, 1L));
            verify(borrowRecordRepository, never()).save(any());
        }

        @Test
        @DisplayName("should throw when member has reached borrowing limit")
        void shouldThrow_WhenBorrowLimitReached() {
            when(memberRepository.findById(1L)).thenReturn(Optional.of(sampleMember));
            when(bookRepository.findById(1L)).thenReturn(Optional.of(sampleBook));
            when(borrowRecordRepository.countActiveBorrowsByMember(1L))
                    .thenReturn(sampleMember.getMembershipType().getMaxBooks());

            assertThrows(BorrowLimitExceededException.class, () -> borrowService.borrowBook(1L, 1L));
            verify(borrowRecordRepository, never()).save(any());
            verify(bookRepository, never()).save(any());
        }

        @Test
        @DisplayName("should throw when member already has this book borrowed")
        void shouldThrow_WhenDuplicateBorrow() {
            when(memberRepository.findById(1L)).thenReturn(Optional.of(sampleMember));
            when(bookRepository.findById(1L)).thenReturn(Optional.of(sampleBook));
            when(borrowRecordRepository.countActiveBorrowsByMember(1L)).thenReturn(1);
            when(borrowRecordRepository.existsByBookIdAndMemberIdAndStatus(1L, 1L, BorrowStatus.BORROWED))
                    .thenReturn(true);

            assertThrows(IllegalStateException.class, () -> borrowService.borrowBook(1L, 1L));
            verify(borrowRecordRepository, never()).save(any());
            verify(bookRepository, never()).save(any());
        }

        @Test
        @DisplayName("should throw when inactive member tries to borrow")
        void shouldThrow_WhenMemberInactive() {
            sampleMember.setActive(false);
            when(memberRepository.findById(1L)).thenReturn(Optional.of(sampleMember));

            IllegalStateException exception = assertThrows(IllegalStateException.class, () -> borrowService.borrowBook(1L, 1L));

            assertEquals("Inactive members cannot borrow books", exception.getMessage());
            verify(bookRepository, never()).findById(any());
            verify(borrowRecordRepository, never()).save(any());
        }

        @Test
        @DisplayName("should decrease available copies after successful borrow")
        void shouldDecreaseAvailableCopies() {
            when(memberRepository.findById(1L)).thenReturn(Optional.of(sampleMember));
            when(bookRepository.findById(1L)).thenReturn(Optional.of(sampleBook));
            when(borrowRecordRepository.countActiveBorrowsByMember(1L)).thenReturn(0);
            when(borrowRecordRepository.existsByBookIdAndMemberIdAndStatus(1L, 1L, BorrowStatus.BORROWED))
                    .thenReturn(false);
            when(borrowRecordRepository.save(any(BorrowRecord.class)))
                    .thenAnswer(invocation -> {
                        BorrowRecord record = invocation.getArgument(0);
                        record.setId(1L);
                        return record;
                    });
            when(bookRepository.save(any(Book.class))).thenReturn(sampleBook);
            ArgumentCaptor<Book> bookCaptor = ArgumentCaptor.forClass(Book.class);

            borrowService.borrowBook(1L, 1L);

            verify(bookRepository).save(bookCaptor.capture());
            assertEquals(2, bookCaptor.getValue().getAvailableCopies());
        }
    }

    @Nested
    @DisplayName("returnBook()")
    class ReturnBookTests {

        @Test
        @DisplayName("should successfully return a borrowed book")
        void shouldReturnBook_WhenBorrowed() {
            BorrowRecord record = new BorrowRecord(sampleBook, sampleMember);
            record.setId(1L);
            when(borrowRecordRepository.findById(1L)).thenReturn(Optional.of(record));
            ArgumentCaptor<BorrowRecord> recordCaptor = ArgumentCaptor.forClass(BorrowRecord.class);
            ArgumentCaptor<Book> bookCaptor = ArgumentCaptor.forClass(Book.class);

            BorrowResponse response = borrowService.returnBook(1L);

            verify(borrowRecordRepository).save(recordCaptor.capture());
            verify(bookRepository).save(bookCaptor.capture());
            assertEquals(BorrowStatus.RETURNED, recordCaptor.getValue().getStatus());
            assertEquals(LocalDate.now(), recordCaptor.getValue().getReturnDate());
            assertEquals(4, bookCaptor.getValue().getAvailableCopies());
            assertEquals(BorrowStatus.RETURNED, response.getStatus());
            assertEquals(LocalDate.now(), response.getReturnDate());
        }

        @Test
        @DisplayName("should throw when trying to return an already returned book")
        void shouldThrow_WhenAlreadyReturned() {
            BorrowRecord record = new BorrowRecord(sampleBook, sampleMember);
            record.setId(1L);
            record.setStatus(BorrowStatus.RETURNED);
            when(borrowRecordRepository.findById(1L)).thenReturn(Optional.of(record));

            IllegalStateException exception = assertThrows(IllegalStateException.class, () -> borrowService.returnBook(1L));

            assertEquals("This book has already been returned", exception.getMessage());
            verify(borrowRecordRepository, never()).save(any());
            verify(bookRepository, never()).save(any());
        }

        @Test
        @DisplayName("should throw when borrow record not found")
        void shouldThrow_WhenRecordNotFound() {
            when(borrowRecordRepository.findById(99L)).thenReturn(Optional.empty());

            IllegalStateException exception = assertThrows(IllegalStateException.class, () -> borrowService.returnBook(99L));

            assertEquals("Borrow record not found: 99", exception.getMessage());
            verify(borrowRecordRepository, never()).save(any());
            verify(bookRepository, never()).save(any());
        }
    }
}
