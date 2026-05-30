package com.conceptcoding.interviewquestions.book_my_show.service;

import com.conceptcoding.interviewquestions.book_my_show.entities.Movie;
import com.conceptcoding.interviewquestions.book_my_show.entities.Screen;
import com.conceptcoding.interviewquestions.book_my_show.entities.Show;
import com.conceptcoding.interviewquestions.book_my_show.entities.Theatre;
import com.conceptcoding.interviewquestions.book_my_show.enums.City;

import java.time.LocalDate;
import java.util.*;

public class TheatreService {

    private final Map<City, List<Theatre>> cityTheatres = new HashMap<>();

    public void addTheatre(Theatre theatre) {
        City city = theatre.getCity();
        if (!cityTheatres.containsKey(city)) {
            cityTheatres.put(city, new ArrayList<>());
        }
        cityTheatres.get(city).add(theatre);
    }

    public Set<Movie> getMovies(City city, LocalDate date) {
        Set<Movie> movies = new HashSet<>();
        List<Theatre> theatres = cityTheatres.getOrDefault(city, new ArrayList<>());

        for (Theatre theatre : theatres) {
            for (Screen screen : theatre.getScreens()) {
                List<Show> shows = screen.getShows(date);
                for (Show show : shows) {
                    movies.add(show.getMovie());
                }
            }
        }
        return movies;
    }

    public List<Theatre> getTheatres(City city, Movie movie, LocalDate date) {
        List<Theatre> theatres = cityTheatres.getOrDefault(city, new ArrayList<>());
        List<Theatre> result = new ArrayList<>();

        for (Theatre theatre : theatres) {
            boolean hasMovie = false;
            for (Screen screen : theatre.getScreens()) {
                List<Show> shows = screen.getShows(date);
                for (Show show : shows) {
                    if (show.getMovie().equals(movie)) {
                        hasMovie = true;
                        break;
                    }
                }
                if (hasMovie) break;
            }
            if (hasMovie) {
                result.add(theatre);
            }
        }
        return result;
    }

    public List<Show> getShows(Movie movie, LocalDate date, Theatre theatre) {
        List<Show> result = new ArrayList<>();

        for (Screen screen : theatre.getScreens()) {
            List<Show> shows = screen.getShows(date);
            for (Show show : shows) {
                if (show.getMovie().equals(movie)) {
                    result.add(show);
                }
            }
        }
        return result;
    }
}

