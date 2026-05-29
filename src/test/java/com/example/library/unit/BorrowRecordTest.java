package com.example.library.unit;

import com.example.library.model.Book;
import com.example.library.model.BorrowRecord;
import com.example.library.model.BorrowStatus;
import com.example.library.model.Genre;
import com.example.library.model.Member;
import com.example.library.model.MembershipType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BorrowRecordTest {

    private Book createSampleBook() {
        Book book = new Book("978-0-13-468599-1", "Clean Code", "Robert C. Martin", 3, Genre.TECHNOLOGY);
        book.setId(1L);
        return book;
    }

    private Member createSampleMember() {
        Member member = new Member("Alice", "alice@example.com", MembershipType.STANDARD);
        member.setId(1L);
        return member;
    }

    @Nested
    @DisplayName("calculateFine()")
    class CalculateFineTests {

        @Test
        @DisplayName("should return 0 when book is returned on time")
        void shouldReturnZeroFine_WhenReturnedOnTime() {
            BorrowRecord record = new BorrowRecord(createSampleBook(), createSampleMember());
            record.setReturnDate(record.getDueDate());

            assertEquals(0.0, record.calculateFine());
        }

        @Test
        @DisplayName("should return 0 when book is returned before due date")
        void shouldReturnZeroFine_WhenReturnedEarly() {
            BorrowRecord record = new BorrowRecord(createSampleBook(), createSampleMember());
            record.setReturnDate(record.getBorrowDate().plusDays(5));

            assertEquals(0.0, record.calculateFine());
        }

        @Test
        @DisplayName("should calculate correct fine when returned 3 days late")
        void shouldCalculateCorrectFine_WhenReturnedLate() {
            BorrowRecord record = new BorrowRecord(createSampleBook(), createSampleMember());
            record.setReturnDate(record.getDueDate().plusDays(3));

            double expectedFine = 3 * BorrowRecord.DAILY_FINE_RATE;
            assertEquals(expectedFine, record.calculateFine());
        }

        @Test
        @DisplayName("should return 0 when book is not yet returned")
        void shouldReturnZeroFine_WhenNotYetReturned() {
            BorrowRecord record = new BorrowRecord(createSampleBook(), createSampleMember());

            assertEquals(0.0, record.calculateFine());
        }
    }

    @Nested
    @DisplayName("isOverdue()")
    class IsOverdueTests {

        @Test
        @DisplayName("should return true when checked after due date and still borrowed")
        void shouldBeOverdue_WhenPastDueDateAndStillBorrowed() {
            BorrowRecord record = new BorrowRecord(createSampleBook(), createSampleMember());

            assertTrue(record.isOverdue(record.getDueDate().plusDays(1)));
        }

        @Test
        @DisplayName("should return false when checked before due date")
        void shouldNotBeOverdue_WhenBeforeDueDate() {
            BorrowRecord record = new BorrowRecord(createSampleBook(), createSampleMember());

            assertFalse(record.isOverdue(record.getDueDate().minusDays(1)));
        }

        @Test
        @DisplayName("should return false when book is already returned (even if past due)")
        void shouldNotBeOverdue_WhenAlreadyReturned() {
            BorrowRecord record = new BorrowRecord(createSampleBook(), createSampleMember());
            record.setStatus(BorrowStatus.RETURNED);

            assertFalse(record.isOverdue(record.getDueDate().plusDays(1)));
        }

        @Test
        @DisplayName("should return false on exactly the due date")
        void shouldNotBeOverdue_OnExactDueDate() {
            BorrowRecord record = new BorrowRecord(createSampleBook(), createSampleMember());

            assertFalse(record.isOverdue(record.getDueDate()));
        }
    }

    @Nested
    @DisplayName("Constructor / default values")
    class ConstructorTests {

        @Test
        @DisplayName("should set borrow date to today")
        void shouldSetBorrowDateToToday() {
            LocalDate beforeCreation = LocalDate.now();
            BorrowRecord record = new BorrowRecord(createSampleBook(), createSampleMember());
            LocalDate afterCreation = LocalDate.now();

            assertFalse(record.getBorrowDate().isBefore(beforeCreation));
            assertFalse(record.getBorrowDate().isAfter(afterCreation));
        }

        @Test
        @DisplayName("should set due date to 14 days from today")
        void shouldSetDueDateTo14DaysFromToday() {
            BorrowRecord record = new BorrowRecord(createSampleBook(), createSampleMember());

            assertEquals(record.getBorrowDate().plusDays(BorrowRecord.STANDARD_BORROW_DAYS), record.getDueDate());
        }

        @Test
        @DisplayName("should set status to BORROWED")
        void shouldSetStatusToBorrowed() {
            BorrowRecord record = new BorrowRecord(createSampleBook(), createSampleMember());

            assertEquals(BorrowStatus.BORROWED, record.getStatus());
        }
    }
}
