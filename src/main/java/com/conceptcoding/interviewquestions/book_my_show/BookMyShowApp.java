package com.conceptcoding.interviewquestions.book_my_show;

import com.conceptcoding.interviewquestions.book_my_show.controllers.BookingController;
import com.conceptcoding.interviewquestions.book_my_show.controllers.TheatreController;
import com.conceptcoding.interviewquestions.book_my_show.entities.*;
import com.conceptcoding.interviewquestions.book_my_show.enums.City;
import com.conceptcoding.interviewquestions.book_my_show.enums.SeatCategory;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.*;

public class BookMyShowApp {

    private TheatreController theatreController;
    private BookingController bookingController;

    public static void main(String[] args) {
        BookMyShowApp app = new BookMyShowApp();
        app.initialize();
        app.userFlow();
    }


    private void initialize() {
        theatreController = new TheatreController();
        bookingController = new BookingController();


        /*
         * 1. Create Movies
         */
        Movie baahubali = new Movie("BAAHUBALI");
        Movie avengers = new Movie("AVENGERS");


        /*
         * 2. Create Theatre -> Screen -> Seats
         */
        Screen inoxScreen1 = new Screen(1, createSeats());
        Theatre inoxTheatreBangalore = new Theatre(
                "INOX",
                City.BANGALORE,
                List.of(inoxScreen1)
        );

        Screen pvrScreen1 = new Screen(1, createSeats());
        Theatre pvrTheatreDelhi = new Theatre(
                "PVR",
                City.DELHI,
                List.of(pvrScreen1)
        );

        theatreController.addTheatre(inoxTheatreBangalore);
        theatreController.addTheatre(pvrTheatreDelhi);


        /*
         * 3. Create Shows
         */
        Show inoxMorningShowToday = new Show(
                baahubali,
                inoxScreen1,
                LocalDate.now(),
                LocalTime.of(8, 0)
        );

        Show inoxAfternoonShowToday = new Show(
                baahubali,
                inoxScreen1,
                LocalDate.now(),
                LocalTime.of(15, 0)
        );

        Show inoxEveningShowToday = new Show(
                avengers,
                inoxScreen1,
                LocalDate.now(),
                LocalTime.of(18, 0)
        );


        Show pvrMorningShowTomorrow = new Show(
                baahubali,
                pvrScreen1,
                LocalDate.now().plusDays(1),
                LocalTime.of(9, 0)
        );


        // Attach shows to screens
        inoxScreen1.addShow(inoxMorningShowToday);
        inoxScreen1.addShow(inoxAfternoonShowToday);
        inoxScreen1.addShow(inoxEveningShowToday);
        pvrScreen1.addShow(pvrMorningShowTomorrow);
    }

    /*
     * USER FLOW (END TO END)
     */
    private void userFlow() {

        // User enters system
        User user = new User("U1", "Shrayansh");

        System.out.println("User logged in: Shrayansh");

        // 1. User selects city
        City selectedCity = City.BANGALORE;
        System.out.println("Selected City: " + selectedCity);

        // 2. Show movies running in city for specific date
        LocalDate selectedDate = LocalDate.now();
        System.out.println("Selected Date: " + selectedDate);

        Set<Movie> availableMovies = theatreController.getMovies(selectedCity, selectedDate);
        System.out.println("Movies available:");
        for (Movie movie : availableMovies) {
            System.out.println(" - " + movie.getName());
        }

        // 3. User selects movie
        Movie selectedMovie = availableMovies.iterator().next(); // Selecting first movie
        System.out.println("Selected Movie: " + selectedMovie.getName());


        // 4. Show theatres running the selected movie in city
        List<Theatre> availableTheatres = theatreController.getTheatres(selectedCity, selectedMovie, selectedDate);
        System.out.println("Theatres available:");
        for (Theatre theatre : availableTheatres) {
            System.out.println(" - " + theatre.getName());
        }

        // 5. User selects theatre
        Theatre selectedTheatre = availableTheatres.get(0);
        System.out.println("Selected Theatre: " + selectedTheatre.getName());

        // 6. Show available show times for selected movie, date and theatre
        List<Show> availableShows = theatreController.getShows(selectedMovie, selectedDate, selectedTheatre);

        System.out.println("Shows available:");
        for (Show show : availableShows) {
            System.out.println(" - " + show.getStartTime());
        }

        // 7. User selects show
        Show selectedShow = availableShows.get(0);
        System.out.println("Selected Show Time: " + selectedShow.getStartTime());

        // 8. User selects seats
        List<Integer> selectedSeats = List.of(1, 2, 3);
        System.out.println("Selected Seats: " + selectedSeats);

        // 9. Create booking and process payment
        Booking booking = bookingController.createBooking(user, selectedShow, selectedSeats);

        System.out.println("BOOKING SUCCESSFUL");
        System.out.println("Booking ID: " + booking.getBookingId());
    }

    private List<Seat> createSeats() {
        List<Seat> seats = new ArrayList<>();
        for (int i = 1; i <= 20; i++) {
            seats.add(new Seat(i, SeatCategory.SILVER));
        }
        return seats;
    }
}
