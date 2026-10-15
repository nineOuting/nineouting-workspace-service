package com.nineouting.backend.workspace.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.nineouting.backend.workspace.domain.Folder;
import com.nineouting.backend.workspace.domain.FolderMember;
import com.nineouting.backend.workspace.service.FolderAccess.Snapshot;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * 폴더 접근 규칙 (docs/requirements.md "권한"). DB 없이 Snapshot의 판단만 확인한다.
 * <pre>
 * 워크스페이스 7 (멤버: 관리자 1, 소유자 2, 초대 3, 하위만 초대 4, 아무 데도 없음 5)
 * ├─ 기본 폴더 10
 * └─ 프로젝트 폴더 20 (소유자 2, 초대 3)
 *     └─ 하위 폴더 21 (소유자 2, 초대 4)
 * </pre>
 */
class FolderAccessSnapshotTest {

    private static final long WORKSPACE = 7L;
    private static final long ADMIN = 1L;
    private static final long OWNER = 2L;
    private static final long PROJECT_MEMBER = 3L;
    private static final long SUB_ONLY_MEMBER = 4L;
    private static final long OTHER_MEMBER = 5L;
    private static final long LEFT_USER = 9L;
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 15, 12, 0);

    private final Folder defaultFolder = folder(10L, Folder.createDefault(WORKSPACE));
    private final Folder project = folder(20L, Folder.create(WORKSPACE, null, "기획", OWNER));
    private final Folder sub = folder(21L, Folder.create(WORKSPACE, 20L, "회의", OWNER));

    private final Snapshot snapshot = snapshot(
            List.of(defaultFolder, project, sub),
            List.of(new FolderMember(20L, PROJECT_MEMBER, NOW), new FolderMember(21L, SUB_ONLY_MEMBER, NOW),
                    new FolderMember(20L, LEFT_USER, NOW)),
            ADMIN, OWNER, PROJECT_MEMBER, SUB_ONLY_MEMBER, OTHER_MEMBER);

    @Test
    void 기본_폴더는_워크스페이스_멤버_누구나_들어간다() {
        assertThat(snapshot.accessors(defaultFolder))
                .containsExactlyInAnyOrder(ADMIN, OWNER, PROJECT_MEMBER, SUB_ONLY_MEMBER, OTHER_MEMBER);
    }

    @Test
    void 프로젝트_폴더는_만든_사람과_초대된_사람만_들어간다() {
        assertThat(snapshot.accessors(project)).containsExactlyInAnyOrder(OWNER, PROJECT_MEMBER);
    }

    @Test
    void 관리자도_초대받지_않으면_들어갈_수_없다() {
        assertThat(snapshot.canAccess(project, ADMIN)).isFalse();
        assertThat(snapshot.canAccess(sub, ADMIN)).isFalse();
    }

    @Test
    void 하위_폴더는_상위_폴더_멤버와_하위에_따로_초대된_사람이_들어간다() {
        assertThat(snapshot.accessors(sub)).containsExactlyInAnyOrder(OWNER, PROJECT_MEMBER, SUB_ONLY_MEMBER);
    }

    @Test
    void 하위_폴더에만_초대된_사람은_상위_폴더에_들어갈_수_없다() {
        assertThat(snapshot.canAccess(sub, SUB_ONLY_MEMBER)).isTrue();
        assertThat(snapshot.canAccess(project, SUB_ONLY_MEMBER)).isFalse();
    }

    @Test
    void 워크스페이스를_떠난_사람은_초대_기록이_남아도_들어갈_수_없다() {
        assertThat(snapshot.canAccess(project, LEFT_USER)).isFalse();
        assertThat(snapshot.canAccess(sub, LEFT_USER)).isFalse();
    }

    @Test
    void 소유자가_없는_폴더는_초대된_사람만_들어간다() {
        Folder orphan = folder(30L, Folder.create(WORKSPACE, null, "주인 없음", OWNER));
        orphan.transferOwner(null);
        Snapshot withOrphan = snapshot(List.of(orphan), List.of(new FolderMember(30L, PROJECT_MEMBER, NOW)),
                OWNER, PROJECT_MEMBER);

        assertThat(withOrphan.accessors(orphan)).containsExactly(PROJECT_MEMBER);
    }

    private static Folder folder(Long id, Folder folder) {
        ReflectionTestUtils.setField(folder, "id", id);
        return folder;
    }

    private static Snapshot snapshot(List<Folder> folders, List<FolderMember> invited, Long... workspaceMembers) {
        Map<Long, Folder> byId = folders.stream().collect(Collectors.toMap(Folder::getId, Function.identity()));
        Map<Long, List<FolderMember>> invitedByFolder = invited.stream()
                .collect(Collectors.groupingBy(FolderMember::getFolderId));
        Set<Long> members = new LinkedHashSet<>(Arrays.asList(workspaceMembers));
        return new Snapshot(folders, byId, invitedByFolder, members);
    }
}
