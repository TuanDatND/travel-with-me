package com.coc.sba_treektour.modules.tour.repository;

import com.coc.sba_treektour.modules.tour.entity.EventImage;

import org.springframework.data.jpa.repository.JpaRepository;

public interface ImageRepository extends JpaRepository<EventImage, Long> {}
