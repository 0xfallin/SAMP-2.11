#include "../main.h"
#include <cmath>
#include "../game/game.h"
#include "../game/Models/ModelInfo.h"
#include "../game/Pickup.h"
#include "netgame.h"
#include "pickuppool.h"

extern CGame *pGame;
extern CNetGame *pNetGame;

namespace
{
constexpr int kLastPickupType = PICKUP_ONCE_FOR_MISSION;
constexpr int kFallbackPickupModel = 18631; // question-mark pickup icon

bool IsValidPickup(const PICKUP& pickup)
{
    if (pickup.iType < 0 || pickup.iType > kLastPickupType)
        return false;

    return std::isfinite(pickup.fX) && std::isfinite(pickup.fY) && std::isfinite(pickup.fZ);
}

int ResolvePickupModel(int model)
{
    if (model >= 0 && model < CModelInfo::NUM_MODEL_INFOS &&
        CModelInfo::ms_modelInfoPtrs[model] != nullptr)
        return model;

    if (CModelInfo::ms_modelInfoPtrs[kFallbackPickupModel] != nullptr)
        return kFallbackPickupModel;

    return -1;
}
}

CPickupPool::CPickupPool()
{
    memset(m_Pickups, 0, sizeof(m_Pickups));

    for (int i = 0; i < MAX_PICKUPS; i++)
    {
        m_dwHnd[i] = 0;
        m_iTimer[i] = 0;
        m_dwGTAId[i] = 0xFFFFFFFF;
        m_bPending[i] = false;
        memset(&m_droppedWeapon[i], 0, sizeof(DROPPED_WEAPON));
        memset(&m_Pickups[i], 0, sizeof(PICKUP));
    }
}

CPickupPool::~CPickupPool()
{
    for (int i = 0; i < MAX_PICKUPS; i++)
        ClearSlot(i);
}

void CPickupPool::Process()
{
    for (int i = 0; i < MAX_PICKUPS; i++)
    {
        if (m_bPending[i] && TryCreate(i) != CreateResult::Created)
            continue;

        if (m_dwHnd[i] != 0 && m_dwHnd[i] != 0xFFFFFFFF)
        {
            int nResult = 0;
            if (ScriptCommand(&is_pickup_picked_up, m_dwHnd[i], &nResult) && nResult)
            {
                if (m_droppedWeapon[i].bDroppedWeapon || m_Pickups[i].iType == 14)
                {
                    RakNet::BitStream bsPickup;
                    if (!m_droppedWeapon[i].bDroppedWeapon)
                    {
                        bsPickup.Write(i);
                        if (pNetGame && pNetGame->GetRakClient())
                            pNetGame->GetRakClient()->RPC(&RPC_PickedUpPickup, &bsPickup, HIGH_PRIORITY, RELIABLE_SEQUENCED, 0, false, UNASSIGNED_NETWORK_ID, nullptr);
                    }
                    else
                    {
                        bsPickup.Write(m_droppedWeapon[i].PlayerID);
                        if (pNetGame && pNetGame->GetRakClient())
                            pNetGame->GetRakClient()->RPC(&RPC_PickedUpPickup, &bsPickup, HIGH_PRIORITY, RELIABLE_SEQUENCED, 0, false, UNASSIGNED_NETWORK_ID, nullptr);
                    }
                }
            }
            else if(m_iTimer[i] > 0)
            {
                m_iTimer[i]--;
            }
        }
    }
}

void CPickupPool::New(PICKUP *pPickup, int iPickup)
{
    if (!pPickup) return;
    if (iPickup < 0 || iPickup >= MAX_PICKUPS) return;
    if (!IsValidPickup(*pPickup)) return;

    const int model = ResolvePickupModel(pPickup->iModel);
    if (model < 0)
        return;

    ClearSlot(iPickup);

    memcpy(&m_Pickups[iPickup], pPickup, sizeof(PICKUP));
    m_Pickups[iPickup].iModel = model;
    m_droppedWeapon[iPickup].bDroppedWeapon = false;
    m_droppedWeapon[iPickup].PlayerID = -1;
    m_bPending[iPickup] = true;

    if (TryCreate(iPickup) == CreateResult::Failed)
        ClearSlot(iPickup);
}

void CPickupPool::Destroy(int iPickup)
{
    ClearSlot(iPickup);
}

CPickupPool::CreateResult CPickupPool::TryCreate(int iPickup)
{
    if (!pGame)
        return CreateResult::Failed;

    PICKUP& pickup = m_Pickups[iPickup];
    const int model = pickup.iModel;

    if (!pGame->IsModelLoaded(model))
    {
        pGame->RequestModel(static_cast<uint16_t>(model));
        pGame->LoadRequestedModels();

        if (!pGame->IsModelLoaded(model))
            return CreateResult::Waiting;
    }

    int gtaId = -1;
    const uintptr_t hnd = pGame->CreatePickup(model, pickup.iType,
                                               pickup.fX, pickup.fY, pickup.fZ, &gtaId);

    if (hnd == 0 || hnd == 0xFFFFFFFF || gtaId == -1 || gtaId == 0xFFFFFFFF)
        return CreateResult::Failed;

    m_dwHnd[iPickup] = hnd;
    m_dwGTAId[iPickup] = gtaId;
    m_bPending[iPickup] = false;
    return CreateResult::Created;
}

void CPickupPool::ClearSlot(int iPickup)
{
    if (iPickup < 0 || iPickup >= MAX_PICKUPS) return;

    const bool hasHandle = m_dwHnd[iPickup] != 0 && m_dwHnd[iPickup] != 0xFFFFFFFF;
    if (hasHandle)
        ScriptCommand(&destroy_pickup, m_dwHnd[iPickup]);

    m_dwHnd[iPickup] = 0;
    m_dwGTAId[iPickup] = 0xFFFFFFFF;
    m_iTimer[iPickup] = 0;
    m_bPending[iPickup] = false;
    memset(&m_droppedWeapon[iPickup], 0, sizeof(DROPPED_WEAPON));
    memset(&m_Pickups[iPickup], 0, sizeof(PICKUP));
}

int CPickupPool::GetIDFromGTAId(int dwGTAId)
{
    if (dwGTAId == -1 || dwGTAId == 0xFFFFFFFF) return -1;

    for (int i = 0; i < MAX_PICKUPS; i++)
    {
        if (m_dwGTAId[i] == dwGTAId && m_dwHnd[i] != 0 && m_dwHnd[i] != 0xFFFFFFFF) {
            return i;
        }
    }

    return -1;
}

void CPickupPool::PickedUp(int dwGTAId)
{
    if (dwGTAId == -1 || dwGTAId == 0xFFFFFFFF) return;

    int PickupID = GetIDFromGTAId(dwGTAId);
    if (PickupID == -1) return;
    if (PickupID >= MAX_PICKUPS) return;
    if (m_dwHnd[PickupID] == 0 || m_dwHnd[PickupID] == 0xFFFFFFFF) return;
    if (m_iTimer[PickupID] != 0) return;
    if (m_droppedWeapon[PickupID].bDroppedWeapon) return;

    RakNet::BitStream bsPickup;
    bsPickup.Write(PickupID);
    if (pNetGame && pNetGame->GetRakClient())
        pNetGame->GetRakClient()->RPC(&RPC_PickedUpPickup, &bsPickup, HIGH_PRIORITY, RELIABLE_ORDERED, 0, false, UNASSIGNED_NETWORK_ID, nullptr);
    m_iTimer[PickupID] = 15;
}