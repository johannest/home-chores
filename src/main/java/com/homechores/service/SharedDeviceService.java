package com.homechores.service;

import com.homechores.domain.ChoreTask;
import com.homechores.domain.ChoreTaskRepository;
import com.homechores.domain.Completion;
import com.homechores.domain.InputLimits;
import com.homechores.domain.Member;
import com.homechores.domain.MemberRepository;
import com.homechores.domain.SharedDevice;
import com.homechores.domain.SharedDeviceRepository;
import com.homechores.service.ChoreService.CompleteOutcome;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The family's shared device (§4.1.3): pairing it with the admin PIN, proving it on each
 * visit, and everything it does <em>for</em> a member. Every action here takes the device
 * and the member it acts for, and refuses unless both — and the chore — are in the same home:
 * the chips on the device's screen are server-rendered, but the rule lives here, not there.
 */
@Service
public class SharedDeviceService {

    private final SharedDeviceRepository devices;
    private final MemberRepository members;
    private final ChoreTaskRepository tasks;
    private final ChoreService chores;
    private final HomeState homeState;

    public SharedDeviceService(SharedDeviceRepository devices, MemberRepository members,
                               ChoreTaskRepository tasks, ChoreService chores, HomeState homeState) {
        this.devices = devices;
        this.members = members;
        this.tasks = tasks;
        this.chores = chores;
        this.homeState = homeState;
    }

    /** A freshly paired device and the one copy of its plaintext secret, for the browser. */
    public record Paired(SharedDevice device, String secret) {
    }

    /** Why pairing did not happen. */
    public enum PairResult { PAIRED, WRONG_PIN, TOO_MANY, UNKNOWN }

    /** The outcome of a pairing attempt; {@code paired} is set only for {@link PairResult#PAIRED}. */
    public record PairOutcome(PairResult result, Paired paired) {
    }

    /**
     * Pairs the browser that knows the home code <em>and</em> the admin PIN as a shared device
     * of that home. The PIN is the admin credential, so pairing is the admin's consent and no
     * further gate is needed; a wrong PIN counts against the same 5-tries lock as every other
     * PIN door. A blank name becomes the home's default wording for one.
     */
    @Transactional
    public PairOutcome pair(String code, String pin, String name, String defaultName) {
        String norm = ChoreService.normalizeCode(code);
        if (chores.findHome(norm).isEmpty()) {
            return new PairOutcome(PairResult.UNKNOWN, null);
        }
        if (!chores.verifyAdminPin(norm, pin)) {
            return new PairOutcome(PairResult.WRONG_PIN, null);
        }
        if (devices.countByHomeCode(norm) >= SharedDevice.MAX_PER_HOME) {
            return new PairOutcome(PairResult.TOO_MANY, null);
        }
        String clean = name == null || name.isBlank() ? defaultName : name.trim();
        String secret = ChoreService.generateToken();
        SharedDevice device = devices.save(new SharedDevice(norm,
                InputLimits.clip(clean, InputLimits.MEMBER_NAME), ChoreService.sha256(secret)));
        chores.touchHome(norm);
        homeState.bump(norm);
        return new PairOutcome(PairResult.PAIRED, new Paired(device, secret));
    }

    /** Whether this secret is the one issued to the device — constant-time, like a member's. */
    public boolean verify(Long deviceId, String secret) {
        if (deviceId == null || secret == null || secret.isBlank()) {
            return false;
        }
        return devices.findById(deviceId)
                .map(d -> ChoreService.secretMatches(d.getSecretHash(), secret))
                .orElse(false);
    }

    public Optional<SharedDevice> find(Long deviceId) {
        return deviceId == null ? Optional.empty() : devices.findById(deviceId);
    }

    public List<SharedDevice> devicesOf(String homeCode) {
        return devices.findByHomeCodeOrderByPairedAtAsc(homeCode);
    }

    /** The device opened the board: a visit for retention, and a timestamp for the admin. */
    @Transactional
    public void seen(Long deviceId) {
        devices.findById(deviceId).ifPresent(d -> {
            d.setLastSeenAt(Instant.now());
            devices.save(d);
            chores.touchHome(d.getHomeCode());
        });
    }

    /** An admin of the device's home removes it; its secret stops working at once and the
     *  open board shows itself out on the bump. */
    @Transactional
    public boolean unpair(Long deviceId, Long adminId) {
        SharedDevice device = devices.findById(deviceId).orElse(null);
        if (device == null) {
            return false;
        }
        if (members.findAdmin(device.getHomeCode(), adminId).isEmpty()) {
            throw new IllegalArgumentException("Not an admin of this home");
        }
        remove(device);
        return true;
    }

    /**
     * The device signing itself out (§4.1.3): the same row deletion as an admin's unpair, so the
     * secret left in the browser stops working and the home's five device slots get one back.
     * No admin check, because it only ever removes the caller — the id comes from the device's
     * own session, never from the client — and pairing again still needs the admin PIN.
     */
    @Transactional
    public boolean leave(Long deviceId) {
        SharedDevice device = devices.findById(deviceId).orElse(null);
        if (device == null) {
            return false;
        }
        remove(device);
        return true;
    }

    private void remove(SharedDevice device) {
        devices.delete(device);
        homeState.bump(device.getHomeCode());
    }

    /** A chore tapped on the device and attributed to {@code memberId}: every lock in §4.3
     *  runs for that member, exactly as if they had tapped it on their own phone. */
    @Transactional
    public CompleteOutcome complete(Long deviceId, Long taskId, Long memberId, ZoneId zone) {
        Member member = memberFor(deviceId, memberId);
        taskOf(member.getHomeCode(), taskId);
        return chores.complete(taskId, memberId, zone);
    }

    /** "I'll do it", on the device, for the member picked. */
    @Transactional
    public boolean book(Long deviceId, Long taskId, Long memberId) {
        Member member = memberFor(deviceId, memberId);
        taskOf(member.getHomeCode(), taskId);
        return chores.bookChore(taskId, memberId);
    }

    /** Clears whoever's booking is on the chore: the device is the household's, and a
     *  booking left behind by a member who went out would otherwise block the kitchen. */
    @Transactional
    public void cancelBooking(Long deviceId, Long taskId) {
        SharedDevice device = deviceOf(deviceId);
        ChoreTask task = taskOf(device.getHomeCode(), taskId);
        chores.cancelBooking(taskId, task.getBookedByMemberId());
    }

    /** Other help written on the device for the member picked; pending like their own would be. */
    @Transactional
    public Optional<Completion> logOtherHelp(Long deviceId, Long memberId, String description) {
        Member member = memberFor(deviceId, memberId);
        return chores.logOtherHelp(member.getHomeCode(), memberId, description);
    }

    /** The member the device acts for, if device and member are in the same home. */
    public Member memberFor(Long deviceId, Long memberId) {
        SharedDevice device = deviceOf(deviceId);
        Member member = memberId == null ? null : members.findById(memberId).orElse(null);
        if (member == null || !member.getHomeCode().equals(device.getHomeCode())) {
            throw new IllegalArgumentException("Member is not in the device's home");
        }
        return member;
    }

    private SharedDevice deviceOf(Long deviceId) {
        return find(deviceId).orElseThrow(() -> new IllegalArgumentException("Unknown device"));
    }

    private ChoreTask taskOf(String homeCode, Long taskId) {
        ChoreTask task = taskId == null ? null : tasks.findById(taskId).orElse(null);
        if (task == null || !task.getHomeCode().equals(homeCode)) {
            throw new IllegalArgumentException("Chore is not in the device's home");
        }
        return task;
    }
}
