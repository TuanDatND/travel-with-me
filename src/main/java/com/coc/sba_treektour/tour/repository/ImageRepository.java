package com.coc.sba_treektour.tour.repository;

import com.coc.sba_treektour.tour.entity.EventImage;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ImageRepository extends JpaRepository<EventImage, Long> {}
