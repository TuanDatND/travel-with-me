package com.coc.sba_treektour.branch.entity;

import com.coc.sba_treektour.account.entity.User;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "staff")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Staff {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "staff_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "branch_id", nullable = false)
    private Branch branch;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "position", length = 100)
    private String position;

    @Column(name = "status", length = 30, nullable = false)
    @Builder.Default
    private String status = "ACTIVE"; // ACTIVE, INACTIVE
}
