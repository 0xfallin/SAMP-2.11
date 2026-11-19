#include "gui/gui.h"
#include "game/game.h"
#include "net/netgame.h"
#include "util/CUtil.h"

#include <atomic>
#include <cmath>
#include <cstring>
#include <cstdio>
#include <algorithm>
#include <string>

extern UI* pUI;
extern CGame* pGame;

namespace
{
	constexpr float kPi = 3.14159265358979f;
	constexpr int   kGroups = 8;
	constexpr int   kMaxPerGroup = 16;

	/* request from the UI thread, applied on the game thread */
	std::atomic<int> g_pendingWeapon{ -1 };

	struct WheelWeapon
	{
		uint8_t     id;
		const char* name;
		const char* abbr;  /* fallback glyph when no icon texture exists */
		const char* icon;  /* texture name; "wheel_<icon>" is looked up in the "samp" database first */
	};

	/* SA weapon ids -> display info */
	const WheelWeapon kWeapons[] = {
		{  0, "Fists",           "FIST", "fist"          },
		{  1, "Brass Knuckles",  "BRKN", "brassknuckle"  },
		{  2, "Golf Club",       "GOLF", "golfclub"      },
		{  3, "Nightstick",      "STCK", "nitestick"     },
		{  4, "Knife",           "KNFE", "knifecur"      },
		{  5, "Baseball Bat",    "BAT",  "bat"           },
		{  6, "Shovel",          "SHVL", "shovel"        },
		{  7, "Pool Cue",        "CUE",  "poolcue"       },
		{  8, "Katana",          "KTNA", "katana"        },
		{  9, "Chainsaw",        "SAW",  "chnsaw"        },
		{ 10, "Dildo",           "DILD", "dildo"         },
		{ 11, "Dildo",           "DILD", "dildo2"        },
		{ 12, "Vibrator",        "VIBR", "vibrator"      },
		{ 13, "Vibrator",        "VIBR", "vibrator2"     },
		{ 14, "Flowers",         "FLWR", "flowers"       },
		{ 15, "Cane",            "CANE", "cane"          },
		{ 16, "Grenade",         "GREN", "grenade"       },
		{ 17, "Tear Gas",        "GAS",  "teargas"       },
		{ 18, "Molotov",         "MOLO", "molotov"       },
		{ 22, "Pistol",          "9MM",  "colt45"        },
		{ 23, "Silenced Pistol", "SIL",  "silenced"      },
		{ 24, "Desert Eagle",    "DEAG", "desert_eagle"  },
		{ 25, "Shotgun",         "SHOT", "chromegun"     },
		{ 26, "Sawn-off",        "SAWN", "sawnoff"       },
		{ 27, "Combat Shotgun",  "SPAS", "shotgspa"      },
		{ 28, "Micro SMG",       "UZI",  "micro_uzi"     },
		{ 29, "MP5",             "MP5",  "mp5lng"        },
		{ 30, "AK-47",           "AK47", "ak47"          },
		{ 31, "M4",              "M4",   "m4"            },
		{ 32, "Tec-9",           "TEC9", "tec9"          },
		{ 33, "Country Rifle",   "RIFL", "cuntgun"       },
		{ 34, "Sniper Rifle",    "SNPR", "sniper"        },
		{ 35, "RPG",             "RPG",  "rocketla"      },
		{ 36, "Heat Seeker",     "HEAT", "heatseek"      },
		{ 37, "Flamethrower",    "FLAM", "flame"         },
		{ 38, "Minigun",         "MINI", "minigun"       },
		{ 39, "Satchel Charge",  "SATC", "satchel"       },
		{ 40, "Detonator",       "DETO", "detonator"     },
		{ 41, "Spray Can",       "SPRY", "spraycan"      },
		{ 42, "Fire Extinguisher","FIRE","fire_ex"       },
		{ 43, "Camera",          "CAM",  "camera"        },
		{ 44, "Night Vision",    "NVG",  "nvgoggles"     },
		{ 45, "Thermal Vision",  "IR",   "irgoggles"     },
		{ 46, "Parachute",       "PARA", "parachute"     },
	};
	constexpr int kWeaponCount = (int)(sizeof(kWeapons) / sizeof(kWeapons[0]));

	/* GTA V order, clockwise from the top */
	const char* const kGroupNames[kGroups] = {
		"MELEE", "HANDGUNS", "SMGS", "SHOTGUNS", "ASSAULT", "SNIPERS", "HEAVY", "THROWN"
	};

	struct GroupDef { int count; uint8_t ids[kMaxPerGroup]; };
	const GroupDef kGroupDefs[kGroups] = {
		{ 16, { 0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15 } }, /* melee */
		{  3, { 22, 23, 24 } },                           /* handguns */
		{  3, { 28, 29, 32 } },                           /* smg     */
		{  3, { 25, 26, 27 } },                           /* shotguns */
		{  2, { 30, 31 } },                               /* assault */
		{  2, { 33, 34 } },                               /* snipers */
		{  4, { 35, 36, 37, 38 } },                       /* heavy   */
		{ 11, { 16, 17, 18, 39, 40, 41, 42, 43, 44, 45, 46 } }, /* thrown / misc */
	};

	/* melee, detonator, camera, goggles and parachute are present without ammo */
	bool needsAmmo(int id)
	{
		return (id >= 16 && id <= 39) || id == 41 || id == 42;
	}

	int weaponIndex(int id)
	{
		for (int i = 0; i < kWeaponCount; i++)
			if (kWeapons[i].id == id) return i;
		return -1;
	}

	int groupOf(int id)
	{
		for (int g = 0; g < kGroups; g++)
			for (int i = 0; i < kGroupDefs[g].count; i++)
				if (kGroupDefs[g].ids[i] == id) return g;
		return -1;
	}

	inline int A(float base, float anim) { return (int)(base * anim); }

	void centeredText(ImGuiRenderer* r, const ImVec2& p, const std::string& s, const ImColor& col, float fs)
	{
		ImVec2 sz = r->calculateTextSize(s, fs);
		r->drawText(ImVec2(p.x - sz.x * 0.5f, p.y - sz.y * 0.5f), col, s, false, fs);
	}

	struct Geometry
	{
		ImVec2 c;
		float  R;     /* outer radius of the ring     */
		float  rin;   /* inner radius of the ring     */
		float  dead;  /* centre dead zone: no selection */
	};

	Geometry geometryFor(const Widget* w)
	{
		ImVec2 disp = w && w->parent() ? w->parent()->size() : ImGui::GetIO().DisplaySize;
		Geometry g;
		g.c    = ImVec2(disp.x * 0.5f, disp.y * 0.5f);
		g.R    = std::min(disp.x, disp.y) * 0.42f;
		g.rin  = g.R * 0.55f;
		g.dead = g.R * 0.28f;
		return g;
	}
}

WeaponWheel::WeaponWheel()
	: Widget()
{
	for (int i = 0; i < kGroups; i++) m_last[i] = -1;
	std::memset(m_icons, 0, sizeof(m_icons));
}

int WeaponWheel::TakePendingWeapon()
{
	return g_pendingWeapon.exchange(-1);
}

/* ------------------------------------------------------------------ state */

bool WeaponWheel::canUse() const
{
	if (!pGame || !pUI) return false;
	if (pGame->IsGamePaused()) return false;

	CPlayerPed* ped = pGame->GetPlayerPed();
	if (!ped || !ped->m_pPed) return false;
	if (ped->IsDead() || ped->IsInVehicle()) return false;

	if (pUI->dialog() && pUI->dialog()->visible()) return false;
	if (pUI->keyboard() && pUI->keyboard()->visible()) return false;
	if (pUI->playertablist() && pUI->playertablist()->visible()) return false;
	return true;
}

int WeaponWheel::currentWeapon() const
{
	CPlayerPed* ped = pGame ? pGame->GetPlayerPed() : nullptr;
	if (!ped || !ped->m_pPed) return 0;
	return ped->GetCurrentWeapon();
}

bool WeaponWheel::owned(int weaponId, uint32_t* ammo, uint32_t* clip) const
{
	if (ammo) *ammo = 0;
	if (clip) *clip = 0;
	if (weaponId == 0) return true; /* fists are always available */

	CPlayerPed* ped = pGame ? pGame->GetPlayerPed() : nullptr;
	if (!ped || !ped->m_pPed) return false;

	for (int i = 0; i < 13; i++)
	{
		const CWeapon& w = ped->m_pPed->m_aWeapons[i];
		if ((int)w.dwType == weaponId && (!needsAmmo(weaponId) || w.dwAmmo > 0))
		{
			if (ammo) *ammo = w.dwAmmo;
			if (clip) *clip = w.dwAmmoInClip;
			return true;
		}
	}
	return false;
}

int WeaponWheel::ownedInGroup(int group, int* out, int maxOut) const
{
	int n = 0;
	const GroupDef& gd = kGroupDefs[group];
	for (int i = 0; i < gd.count && n < maxOut; i++)
		if (owned(gd.ids[i])) out[n++] = gd.ids[i];
	return n;
}

/* weapon shown on a sector (and used as target when equipping it) */
int WeaponWheel::representative(int group, int current) const
{
	int list[kMaxPerGroup];
	int n = ownedInGroup(group, list, kMaxPerGroup);
	if (n == 0) return -1;

	for (int i = 0; i < n; i++)
		if (list[i] == current) return current;

	if (m_last[group] >= 0)
		for (int i = 0; i < n; i++)
			if (list[i] == m_last[group]) return m_last[group];

	return list[0];
}

/* what releasing the finger on this sector will equip */
int WeaponWheel::pickTarget(int group, int current) const
{
	int list[kMaxPerGroup];
	int n = ownedInGroup(group, list, kMaxPerGroup);
	if (n == 0) return -1;

	for (int i = 0; i < n; i++)
		if (list[i] == current)
			return list[(i + 1) % n]; /* same sector again -> cycle */

	return representative(group, current);
}

void WeaponWheel::open()
{
	m_open = true;
	m_hover = -1;
	m_pointer = m_eventPointer;
	if (!m_iconsLoaded) loadIcons();
}

void WeaponWheel::close()
{
	m_open = false;
	m_pointer = -1;
}

void WeaponWheel::confirm()
{
	if (m_hover < 0 || m_hover >= kGroups) return;
	if (!canUse()) return;

	int cur = currentWeapon();
	int target = pickTarget(m_hover, cur);
	if (target < 0 || target == cur) return;

	m_last[m_hover] = target;
	g_pendingWeapon.store(target);
}

void WeaponWheel::updateHover(const ImVec2& pos)
{
	/* the wheel centre is the joystick origin: the direction from the centre
	   to the finger picks the sector, so pointing up highlights the top one */
	const Geometry g = geometryFor(this);
	const float dx = pos.x - g.c.x;
	const float dy = pos.y - g.c.y;
	if (dx * dx + dy * dy < g.dead * g.dead)
	{
		m_hover = -1; /* back in the centre means cancel, like GTA V */
		return;
	}

	float a = atan2f(dx, -dy);          /* 0 = up, clockwise */
	if (a < 0.0f) a += 2.0f * kPi;
	const int s = (int)((a + kPi / 8.0f) / (kPi / 4.0f)) % kGroups;
	m_hover = s;
}

/* ------------------------------------------------------------------ touch */

void WeaponWheel::touchEvent(const ImVec2& pos, TouchType type)
{
	if (type == TouchType::push)
	{
		if (m_open)
		{
			m_consumed = true; /* extra finger while open: swallow it */
			return;
		}

		if (visible() && canUse() && contains(pos))
		{
			open();
			m_consumed = true;
		}
		return;
	}

	if (!m_open) return;

	m_consumed = true;

	/* only the finger that opened the wheel drives it: another finger
	   (e.g. the movement stick) must not rotate or close it */
	if (m_pointer >= 0 && m_eventPointer >= 0 && m_eventPointer != m_pointer)
		return;

	if (type == TouchType::move)
	{
		updateHover(pos);
	}
	else if (type == TouchType::pop)
	{
		/* release always closes: the hovered sector is equipped, or nothing
		   when the release happens in the centre dead zone */
		confirm();
		close();
	}
}

/* ------------------------------------------------------------------ icons */

void WeaponWheel::loadIcons()
{
	m_iconsLoaded = true;
	for (int i = 0; i < kWeaponCount && i < 64; i++)
	{
		std::string custom = std::string("wheel_") + kWeapons[i].icon;
		RwTexture* tex = (RwTexture*)CUtil::LoadTextureFromDB("samp", custom.c_str());
		if (!tex)
			tex = (RwTexture*)CUtil::LoadTextureFromDB("mobile", kWeapons[i].icon);
		m_icons[i] = tex;
	}
}

void WeaponWheel::drawWeaponGlyph(ImGuiRenderer* r, int weaponId, const ImVec2& c, float size, const ImColor& color)
{
	int idx = weaponIndex(weaponId);
	if (idx < 0) return;

	RwTexture* tex = idx < 64 ? m_icons[idx] : nullptr;
	if (tex && tex->raster)
	{
		float w = size, h = size * 0.6f;
		r->drawImage(ImVec2(c.x - w * 0.5f, c.y - h * 0.5f), ImVec2(c.x + w * 0.5f, c.y + h * 0.5f), (ImTextureID)tex->raster);
		return;
	}
	centeredText(r, c, kWeapons[idx].abbr, color, UISettings::fontSize() * 0.45f);
}

/* ------------------------------------------------------------------- draw */

void WeaponWheel::draw(ImGuiRenderer* r)
{
	float dt = ImGui::GetIO().DeltaTime;
	if (dt <= 0.0f || dt > 0.1f) dt = 1.0f / 60.0f;

	if (m_open && !canUse()) close(); /* died / got in a car / dialog opened */

	m_anim += (m_open ? 1.0f : -1.0f) * dt * 9.0f;
	m_anim = std::max(0.0f, std::min(1.0f, m_anim));

	if (m_anim > 0.0f)
	{
		drawWheel(r);
		return;
	}

	m_hover = -1;
	if (canUse()) drawTrigger(r);
}

void WeaponWheel::drawTrigger(ImGuiRenderer* r)
{
	ImVec2 p = absolutePosition();
	ImVec2 s = size();
	ImVec2 c(p.x + s.x * 0.5f, p.y + s.y * 0.5f);
	float rad = std::min(s.x, s.y) * 0.5f;

	r->drawCircle(c, rad, ImColor(0, 0, 0, 110), true);
	r->drawCircle(c, rad, ImColor(255, 255, 255, 170), false, 2.0f);
	centeredText(r, c, "WPN", ImColor(255, 255, 255, 230), UISettings::fontSize() * 0.4f);
}

void WeaponWheel::drawWheel(ImGuiRenderer* r)
{
	const float anim = m_anim;
	const Geometry g = geometryFor(this);
	const ImVec2 disp = parent() ? parent()->size() : ImGui::GetIO().DisplaySize;
	const int cur = currentWeapon();
	const int curGroup = groupOf(cur);

	/* dim the world */
	r->drawRect(ImVec2(0, 0), disp, ImColor(0, 0, 0, A(150, anim)), true);

	const float gap    = 0.035f;
	const float half   = kPi / 8.0f - gap;
	const float rmid   = (g.R + g.rin) * 0.5f;
	const float thick  = g.R - g.rin;

	for (int i = 0; i < kGroups; i++)
	{
		const float ang = -kPi / 2.0f + i * (kPi / 4.0f);
		const int rep = representative(i, cur);
		const bool has = rep >= 0;
		const bool hot = (i == m_hover);

		ImColor fill = hot ? ImColor(255, 255, 255, A(225, anim))
		                   : has ? ImColor(15, 15, 15, A(175, anim))
		                         : ImColor(15, 15, 15, A(95, anim));
		r->drawArc(g.c, rmid, ang - half, ang + half, fill, thick, 14);

		/* equipped sector marker */
		if (i == curGroup)
			r->drawArc(g.c, g.R + 4.0f, ang - half, ang + half, ImColor(240, 160, 0, A(255, anim)), 4.0f, 14);

		/* weapon glyph */
		ImVec2 gp(g.c.x + cosf(ang) * rmid, g.c.y + sinf(ang) * rmid);
		ImColor txt = hot ? ImColor(10, 10, 10, A(255, anim))
		                  : has ? ImColor(255, 255, 255, A(255, anim))
		                        : ImColor(255, 255, 255, A(70, anim));
		if (has)
			drawWeaponGlyph(r, rep, gp, thick * 0.80f, txt);
		else
			centeredText(r, gp, "-", txt, UISettings::fontSize() * 0.6f);

		/* sector name on the outside of the ring */
		ImVec2 np(g.c.x + cosf(ang) * (g.R + UISettings::fontSize() * 0.55f),
		          g.c.y + sinf(ang) * (g.R + UISettings::fontSize() * 0.55f));
		centeredText(r, np, kGroupNames[i], ImColor(255, 255, 255, A(hot ? 255 : 150, anim)), UISettings::fontSize() * 0.38f);
	}

	/* centre disc */
	r->drawCircle(g.c, g.rin * 0.92f, ImColor(10, 10, 10, A(190, anim)), true, 1.0f, 64);
	r->drawCircle(g.c, g.rin * 0.92f, ImColor(255, 255, 255, A(60, anim)), false, 2.0f, 64);

	int preview = cur;
	bool emptyGroup = false;
	if (m_hover >= 0)
	{
		preview = pickTarget(m_hover, cur);
		emptyGroup = preview < 0;
	}

	if (emptyGroup)
	{
		centeredText(r, g.c, "EMPTY", ImColor(255, 255, 255, A(160, anim)), UISettings::fontSize() * 0.6f);
	}
	else
	{
		int idx = weaponIndex(preview);
		std::string name = idx >= 0 ? kWeapons[idx].name : "Unknown";
		centeredText(r, ImVec2(g.c.x, g.c.y - g.rin * 0.22f), name, ImColor(255, 255, 255, A(255, anim)), UISettings::fontSize() * 0.6f);

		uint32_t ammo = 0, clip = 0;
		if (owned(preview, &ammo, &clip) && needsAmmo(preview) && ammo > 1)
		{
			char buf[32];
			if (clip > 0 && clip <= ammo)
				snprintf(buf, sizeof(buf), "%u / %u", clip, ammo - clip);
			else
				snprintf(buf, sizeof(buf), "%u", ammo);
			centeredText(r, ImVec2(g.c.x, g.c.y + g.rin * 0.22f), buf, ImColor(240, 160, 0, A(255, anim)), UISettings::fontSize() * 0.6f);
		}

		const char* hint = m_hover < 0 ? "DRAG TO CHOOSE" : (preview == cur ? "" : "RELEASE TO EQUIP");
		if (hint[0])
			centeredText(r, ImVec2(g.c.x, g.c.y + g.rin * 0.62f), hint, ImColor(255, 255, 255, A(120, anim)), UISettings::fontSize() * 0.32f);
	}
}
