#pragma once
#include "game/game.h"
#include "game/util.h"

#include "main.h"

#include "settings.h"
#include "util/CUtil.h"


extern CSettings* pSettings;

class VoiceButton : public Button
{
public:
	VoiceButton() : Button("TALK", UISettings::fontSize() / 2) {
		m_recording = false;
		/* 5:3 aspect ratio */
		loadTextures();
	}

	virtual void draw(ImGuiRenderer* renderer) override
	{
		if(!pSettings || !pSettings->Get().bVoiceChatEnable) return;

		if (countdown > 0 && recording() == 1) countdown--;
		if (countdown == 0 && recording() == 1) setRecording(0);

		/*
		 * The texture may be missing from the player's cache or the "samp"
		 * database may still be streaming when the button is created.
		 * Retry about once per second and never dereference a null texture.
		 */
		if (!m_texture_micro_on || !m_texture_micro_off)
		{
			if (--m_textureRetry <= 0)
			{
				loadTextures();
				m_textureRetry = 60;
			}
		}

		RwTexture* texture = recording() ? m_texture_micro_on : m_texture_micro_off;
		if (!texture || !texture->raster) return;

		renderer->drawImage(absolutePosition(), absolutePosition() + size(), texture->raster);

		//MyLog2("%f,%f",absolutePosition(), absolutePosition() + size());
		//MyLog2("countdown %d", countdown);
		//MyLog2("recording %d", recording());
		//MyLog2("press %d", press);
		
	}

	void touchPopEvent() override
	{
		countdown = 500;
		setRecording(0);
	}

	void touchPushEvent() override
	{
			//setRecording(recording() ^ 1);
		setRecording(1);
		countdown = 500;
	}

	void setRecording(bool recording) 
	{ 
		if (recording == 1) countdown = 200;
		m_recording = recording;
		this->setCaptionColor(m_recording ? ImColor(1.0f, 0.0f, 0.0f) : ImColor(1.0f, 1.0f, 1.0f));
	}

	bool recording() const { return m_recording; }

private:
	void loadTextures()
	{
		if (!m_texture_micro_on)
			m_texture_micro_on = loadTexture("micro_active", "icon_micro_on", "micro_icon");
		if (!m_texture_micro_off)
			m_texture_micro_off = loadTexture("micro_passive", "icon_micro_off", "micro_muted");
	}

	static RwTexture* loadTexture(const char* name, const char* fallback1, const char* fallback2)
	{
		RwTexture* texture = (RwTexture*)CUtil::LoadTextureFromDB("samp", name);
		if (!texture && fallback1)
			texture = (RwTexture*)CUtil::LoadTextureFromDB("samp", fallback1);
		if (!texture && fallback2)
			texture = (RwTexture*)CUtil::LoadTextureFromDB("samp", fallback2);
		return texture;
	}

	bool m_recording;
	RwTexture* m_texture_micro_on = nullptr;
	RwTexture* m_texture_micro_off = nullptr;
	int m_textureRetry = 0;
	//int countdown = 1000;
public:
	int countdown = 1000;
};