#pragma once

#pragma pack(push, 1)

#include "netgame.h"

#define MAX_PICKUPS 4096

typedef struct _PICKUP
{
    int iModel;
    int iType;
    float fX;
    float fY;
    float fZ;
} PICKUP;

typedef struct _DROPPED_WEAPON
{
    bool		bDroppedWeapon;
    PLAYERID	PlayerID;
} DROPPED_WEAPON;
#pragma pack(pop)

class CPickupPool
{
public:
    CPickupPool();
    ~CPickupPool();

    void Process();
    void New(PICKUP *pPickup, int iPickup);
    void Destroy(int iPickup);
    void PickedUp(int dwGTAId);

private:
    enum class CreateResult
    {
        Created,
        Waiting, // model still streaming, retried from Process()
        Failed
    };

    int GetIDFromGTAId(int dwGTAId);
    CreateResult TryCreate(int iPickup);
    void ClearSlot(int iPickup);

private:
    uintptr_t		m_dwHnd[MAX_PICKUPS];
    int				m_dwGTAId[MAX_PICKUPS];
    int				m_iTimer[MAX_PICKUPS];
    bool			m_bPending[MAX_PICKUPS];
    DROPPED_WEAPON	m_droppedWeapon[MAX_PICKUPS];
    PICKUP			m_Pickups[MAX_PICKUPS];
};