package com.chenruichao.controller;

import com.chenruichao.dto.Result;
import com.chenruichao.service.IFollowService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;


@RestController
@RequestMapping("/follow")
@RequiredArgsConstructor
public class FollowController {

    private final IFollowService followService;

    @PutMapping("/{id}/{isFollow}")
    public Result Follow(@PathVariable Long id, @PathVariable Boolean isFollow) {
        return followService.follow(id, isFollow);
    }

    @GetMapping ("/or/not/{id}")
    public Result isFollow(@PathVariable Long id) {
        return followService.isFollow(id);
    }

    @GetMapping("/common/{id}")
    public Result followCommons(@PathVariable Long id) {
        return followService.followCommons(id);
    }
}
