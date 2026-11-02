package xiaozhi.modules.pet.service;

import xiaozhi.modules.pet.vo.PetVO;

/** NFC 领养的展示结果与本次邀请码替换标记。 */
public record NfcAdoptionResult(PetVO pet, boolean replacedInvitePet) { }
