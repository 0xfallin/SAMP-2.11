#pragma once

#include <cstdint>

struct RwTexture;

/*
 * GTA V style weapon wheel.
 *
 * - Small round WPN trigger button on the HUD. HOLD it and DRAG away from the
 *   button like a joystick (the button is the stick origin; the drag direction
 *   selects the sector, up = top sector). RELEASE to equip. Releasing while
 *   still on the button = cancel. The wheel interface itself stays drawn in the
 *   centre of the screen.
 * - 8 sectors, clockwise from the top like GTA V:
 *   melee, handguns, SMGs, shotguns, assault rifles, snipers, heavy, thrown/misc.
 * - Each sector shows the weapon you would get: the equipped one if it belongs to
 *   the sector, otherwise the last one used there, otherwise the first one owned.
 *   Selecting the sector that is already equipped cycles to the next owned weapon.
 * - Centre disc shows the weapon name and ammo (clip / total).
 *
 * Thread model: this widget only READS the ped weapon slots and writes one atomic
 * request. The request is applied on the game thread by CLocalPlayer::Process
 * (WeaponWheel::TakePendingWeapon), the same way the other HUD buttons do it.
 */
class WeaponWheel : public Widget
{
public:
	WeaponWheel();

	void draw(ImGuiRenderer* renderer) override;
	void touchEvent(const ImVec2& pos, TouchType type) override;

	bool isOpen() const { return m_open; }

	/* did the last touchEvent() get swallowed by the wheel (so the game must not see it)? */
	bool consumedTouch() const { return m_consumed; }
	void resetConsumed() { m_consumed = false; }

	/* pointer id of the touch being delivered (set by the input hook) */
	void setTouchPointer(int id) { m_eventPointer = id; }

	/* game thread: returns the requested weapon id, or -1 if nothing is pending */
	static int TakePendingWeapon();

private:
	bool canUse() const;
	void open();
	void close();
	void confirm();
	void updateHover(const ImVec2& pos);
	void loadIcons();

	int  currentWeapon() const;
	int  pickTarget(int group, int current) const;
	bool owned(int weaponId, uint32_t* ammo = nullptr, uint32_t* clip = nullptr) const;
	int  ownedInGroup(int group, int* out, int maxOut) const;
	int  representative(int group, int current) const;

	void drawTrigger(ImGuiRenderer* renderer);
	void drawWheel(ImGuiRenderer* renderer);
	void drawWeaponGlyph(ImGuiRenderer* renderer, int weaponId, const ImVec2& center,
	                     float size, const ImColor& color);

private:
	bool  m_open = false;
	bool  m_consumed = false;
	int   m_hover = -1;
	float m_anim = 0.0f;

	/* finger that opened the wheel, and the finger of the current event */
	int   m_pointer = -1;
	int   m_eventPointer = -1;

	int   m_last[8];
	bool  m_iconsLoaded = false;
	RwTexture* m_icons[64];
};
